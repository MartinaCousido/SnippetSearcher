package com.snippetsearcher.snippets.observability;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// GET /health (en el proxy, /api/health): público y sin token, para los
// synthetics. Responde solo UP (200) o DOWN (503), sin detalles internos.
@RestController
public class HealthController {

	private final SystemHealth health;

	public HealthController(SystemHealth health) {
		this.health = health;
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		return health.isUp()
				? ResponseEntity.ok(Map.of("status", "UP"))
				: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
	}
}
