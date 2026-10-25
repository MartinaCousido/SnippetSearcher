package com.snippetsearcher.snippets.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

// Qué páginas de otro origin pueden llamar a este servicio desde el navegador
// (ver CORS en el README). Con el proxy, la UI y la API están en el mismo
// origin y el navegador no lo aplica, pero se configura igual.
// Los origins salen de CORS_ALLOWED_ORIGINS, separados por comas. Sin la
// variable, no se acepta ningún origin externo.
//
// Cuando entre Spring Security (Auth0), activar http.cors(): usa este mismo
// CorsConfigurationSource. Si no, el preflight (OPTIONS) llega sin token y
// responde 401.
@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${cors.allowed-origins:}") String allowedOrigins) {
		var origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
		if (origins.contains("*")) {
			throw new IllegalStateException("CORS_ALLOWED_ORIGINS no puede tener *: hay que listar los origins");
		}
		var config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	// Sin Spring Security, nadie usa el CorsConfigurationSource: este filtro lo
	// aplica a cada pedido.
	@Bean
	public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
		return new CorsFilter(corsConfigurationSource);
	}
}
