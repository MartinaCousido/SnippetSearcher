package com.snippetsearcher.snippets.observability;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newrelic.api.agent.NewRelic;
import com.snippetsearcher.snippets.analysis.AnalysisTask;

// Saturación de la cola (una de las cuatro señales): cada 30 segundos manda a
// New Relic un evento RedisStreamSample por stream, con su largo y cuántos
// mensajes quedan pendientes. Sin el agente, no hace nada.
@Component
public class RedisStreamMetrics {

	static final String EVENT = "RedisStreamSample";
	static final String GROUP = "snippet-service";

	private static final Logger log = LoggerFactory.getLogger(RedisStreamMetrics.class);

	private final StringRedisTemplate redis;

	public RedisStreamMetrics(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Scheduled(fixedDelayString = "${observability.redis-sample-interval:30000}")
	public void sample() {
		for (var task : AnalysisTask.values()) {
			try {
				NewRelic.getAgent().getInsights().recordCustomEvent(EVENT, sampleOf(task));
			} catch (RuntimeException e) {
				log.warn("No se pudo medir el stream {}", task.stream(), e);
			}
		}
	}

	Map<String, Object> sampleOf(AnalysisTask task) {
		var stream = redis.opsForStream();
		var length = stream.size(task.stream());
		long pending = 0;
		try {
			pending = stream.pending(task.stream(), GROUP).getTotalPendingMessages();
		} catch (RuntimeException e) {
			// El grupo todavía no existe (no arrancó el consumidor): no hay pendientes.
		}
		return Map.of("stream", task.stream(), "length", length == null ? 0L : length, "pending", pending,
				"service", LogContext.SERVICE_NAME);
	}
}
