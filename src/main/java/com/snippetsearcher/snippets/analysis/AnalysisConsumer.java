package com.snippetsearcher.snippets.analysis;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.snippetsearcher.snippets.observability.LogContext;

import jakarta.annotation.PreDestroy;

// Consume los pedidos de lint y formateo en segundo plano (US-12, US-15), con
// un consumer group por stream. Un mensaje se confirma (XACK) recién después
// de guardar el resultado. Si falla, queda pendiente: se vuelve a tomar
// (XCLAIM) cuando pasó retry-after, y al llegar a max-attempts se da por
// perdido (ver Cola de mensajes en Decisiones técnicas).
@Component
@ConditionalOnProperty(name = "analysis.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisConsumer {

	static final String GROUP = "snippet-service";

	private static final Logger log = LoggerFactory.getLogger(AnalysisConsumer.class);

	private final StringRedisTemplate redis;
	private final AnalysisWorker worker;
	private final AnalysisRequests requests;
	private final int maxAttempts;
	private final Duration retryAfter;
	// Cada réplica del servicio es un consumidor distinto del mismo grupo.
	private final String consumer = "snippet-service-" + UUID.randomUUID();

	private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

	public AnalysisConsumer(StringRedisTemplate redis, AnalysisWorker worker, AnalysisRequests requests,
			@Value("${analysis.max-attempts}") int maxAttempts, @Value("${analysis.retry-after}") Duration retryAfter) {
		this.redis = redis;
		this.worker = worker;
		this.requests = requests;
		this.maxAttempts = maxAttempts;
		this.retryAfter = retryAfter;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		container = StreamMessageListenerContainer.create(redis.getConnectionFactory(),
				StreamMessageListenerContainerOptions.builder().pollTimeout(Duration.ofSeconds(1)).build());
		for (var task : AnalysisTask.values()) {
			createGroup(task);
			container.receive(Consumer.from(GROUP, consumer), StreamOffset.create(task.stream(), ReadOffset.lastConsumed()),
					record -> handle(task, record));
		}
		container.start();
		requests.requestNeverAnalyzed();
	}

	@PreDestroy
	public void stop() {
		if (container != null) {
			container.stop();
		}
	}

	// Los mensajes que quedaron sin confirmar (falló el proceso, o se cayó el
	// servicio a mitad de camino) se vuelven a tomar o se dan por perdidos.
	@Scheduled(fixedDelayString = "${analysis.retry-interval}", initialDelayString = "${analysis.retry-interval}")
	public void retryPending() {
		for (var task : AnalysisTask.values()) {
			try {
				retryPending(task);
			} catch (RuntimeException e) {
				log.warn("No se pudieron revisar los pendientes de {}", task, e);
			}
		}
	}

	private void retryPending(AnalysisTask task) {
		var stream = redis.opsForStream();
		for (var pending : stream.pending(task.stream(), GROUP, Range.unbounded(), 100)) {
			if (pending.getElapsedTimeSinceLastDelivery().compareTo(retryAfter) < 0) {
				continue;
			}
			if (pending.getTotalDeliveryCount() >= maxAttempts) {
				stream.range(task.stream(), Range.just(pending.getIdAsString())).forEach(record -> {
					log.warn("Se agotaron los intentos de {} del snippet {}", task, record.getValue());
					worker.giveUp(task, snippetId(record), revision(record));
				});
				stream.acknowledge(task.stream(), GROUP, pending.getId());
				continue;
			}
			stream.claim(task.stream(), GROUP, consumer, retryAfter, pending.getId())
					.forEach(record -> handle(task, record));
		}
	}

	private void handle(AnalysisTask task, MapRecord<String, ?, ?> record) {
		// Las líneas de este mensaje llevan el snippet y el servicio en el contexto.
		MDC.put(LogContext.SERVICE, LogContext.SERVICE_NAME);
		MDC.put(LogContext.SNIPPET_ID, String.valueOf(record.getValue().get(RedisAnalysisQueue.SNIPPET_ID)));
		try {
			worker.process(task, snippetId(record), revision(record));
			redis.opsForStream().acknowledge(task.stream(), GROUP, record.getId());
		} catch (RuntimeException e) {
			// Sin XACK: queda pendiente y se reintenta.
			log.warn("Falló {} del snippet {}; se va a reintentar", task, record.getValue(), e);
		} finally {
			MDC.remove(LogContext.SERVICE);
			MDC.remove(LogContext.SNIPPET_ID);
		}
	}

	// Crea el stream y el grupo si no existen. Si ya existen, Redis responde
	// BUSYGROUP y no pasa nada.
	private void createGroup(AnalysisTask task) {
		try {
			redis.opsForStream().createGroup(task.stream(), ReadOffset.from("0"), GROUP);
		} catch (RedisSystemException e) {
			// El BUSYGROUP viene en la causa: el mensaje de afuera es genérico.
			var cause = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
			if (cause == null || !cause.contains("BUSYGROUP")) {
				throw e;
			}
		}
	}

	private static UUID snippetId(MapRecord<String, ?, ?> record) {
		return UUID.fromString(String.valueOf(record.getValue().get(RedisAnalysisQueue.SNIPPET_ID)));
	}

	private static int revision(MapRecord<String, ?, ?> record) {
		return Integer.parseInt(String.valueOf(record.getValue().get(RedisAnalysisQueue.REVISION)));
	}
}
