package com.snippetsearcher.snippets.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Quién puede llamar a cada endpoint (ver Roles y permisos en Decisiones
// técnicas). Cada pedido lleva el token de Auth0: Spring revisa la firma, el
// iss, el aud y que no esté vencido (application.properties). Sin token
// válido: 401. Sin el permiso que pide la operación: 403.
//
// Sobre cuál snippet puede actuar el usuario (owner o compartido) no se decide
// acá: lo decide Permission Service, desde SnippetService.
//
// Spring convierte cada permiso del claim "scope" en una authority
// SCOPE_<permiso>.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	static final String READ = "SCOPE_read:snippets";
	static final String WRITE = "SCOPE_write:snippets";

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(requests -> requests
						// El health lo usan los healthchecks de Docker, sin token.
						// Desde afuera no se publica (ver Reverse proxy en infra).
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
						// El health del sistema, para los synthetics: solo UP o DOWN.
						.requestMatchers(HttpMethod.GET, "/health").permitAll()
						// La ejecución en vivo (US-9, US-10) abre un WebSocket: el
						// navegador no deja mandar el token en el handshake, así que
						// llega en el primer mensaje y lo revisa LiveExecutionService.
						.requestMatchers(HttpMethod.GET, "/snippets/*/execute").permitAll()
						// Leer: los GET, correr un test y formatear sin guardar.
						.requestMatchers(HttpMethod.GET, "/**").hasAuthority(READ)
						.requestMatchers(HttpMethod.POST, "/snippets/*/tests/run", "/snippets/*/format").hasAuthority(READ)
						// Todo lo demás escribe: crear, editar, borrar, compartir y
						// cambiar reglas.
						.anyRequest().hasAuthority(WRITE))
				.oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()))
				// Con tokens no hay sesión ni cookies, así que tampoco CSRF.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				// Usa el CorsConfigurationSource de CorsConfig. Responde el
				// preflight (OPTIONS) antes de pedir el token, que el navegador
				// no manda en el preflight.
				.cors(withDefaults());
		return http.build();
	}
}
