package com.snippetsearcher.snippets.observability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// ¿Anda todo lo que necesita el sistema? Revisa, en paralelo, la base, Redis,
// Permission Service, el servicio de PrintScript y el Asset Service. Lo usa
// GET /health, que miran los synthetics de New Relic cada minuto.
@Component
public class SystemHealth {

	private static final Logger log = LoggerFactory.getLogger(SystemHealth.class);
	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private final Map<String, BooleanSupplier> checks = new LinkedHashMap<>();

	public SystemHealth(JdbcTemplate jdbc, StringRedisTemplate redis,
			@Value("${permission-service.url}") String permissionUrl,
			@Value("${printscript-service.url}") String printScriptUrl,
			@Value("${asset-service.health-url}") String assetHealthUrl) {
		// Un cliente propio, con tiempos cortos y sin loguear cada chequeo.
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(TIMEOUT);
		factory.setReadTimeout(TIMEOUT);
		var http = RestClient.builder().requestFactory(factory).build();
		checks.put("base de datos", () -> Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class)));
		checks.put("redis", () -> "PONG".equals(redis.execute(connection -> connection.ping(), true)));
		checks.put("permission-service", () -> isUp(http, permissionUrl + "/actuator/health"));
		checks.put("printscript-service", () -> isUp(http, printScriptUrl + "/actuator/health"));
		checks.put("asset-service", () -> isUp(http, assetHealthUrl));
	}

	// true si anda todo. Lo que no anda queda en el log, con su nombre: la
	// respuesta pública no da detalles.
	public boolean isUp() {
		var results = new LinkedHashMap<String, CompletableFuture<Boolean>>();
		checks.forEach((name, check) -> results.put(name, CompletableFuture.supplyAsync(() -> safely(check))
				.completeOnTimeout(false, TIMEOUT.toMillis() + 500, TimeUnit.MILLISECONDS)));
		var down = results.entrySet().stream().filter(result -> !result.getValue().join()).map(Map.Entry::getKey)
				.toList();
		if (!down.isEmpty()) {
			log.warn("health: no responde {}", String.join(", ", down));
		}
		return down.isEmpty();
	}

	private static boolean safely(BooleanSupplier check) {
		try {
			return check.getAsBoolean();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean isUp(RestClient http, String url) {
		return http.get().uri(url).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
	}
}
