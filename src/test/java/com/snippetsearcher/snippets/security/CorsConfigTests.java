package com.snippetsearcher.snippets.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CorsConfigTests {

	// Nunca *: aceptaría pedidos desde cualquier página.
	@Test
	void rejectsAsterisk() {
		assertThrows(IllegalStateException.class,
				() -> new CorsConfig().corsConfigurationSource("https://snippetsearcher-dev.duckdns.org,*"));
	}
}
