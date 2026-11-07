package com.snippetsearcher.snippets.security;

import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Dice quién es el usuario que hace el pedido. Es el único lugar del servicio
// que lo sabe: el userId nunca se toma de un dato que manda el cliente.
// Es el "sub" del token de Auth0, por ejemplo auth0|abc123, que Spring ya
// validó (SecurityConfig).
@Component
public class CurrentUser {

	public String id() {
		return jwt()
				.map(JwtAuthenticationToken::getName)
				.orElseThrow(() -> new IllegalStateException("El pedido no tiene un usuario autenticado"));
	}

	// El token tal como llegó, para reenviárselo a Permission Service. Vacío
	// fuera de un pedido del usuario (por ejemplo, en un trabajo en segundo
	// plano).
	public Optional<String> token() {
		return jwt().map(authentication -> authentication.getToken().getTokenValue());
	}

	private Optional<JwtAuthenticationToken> jwt() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication instanceof JwtAuthenticationToken jwt ? Optional.of(jwt) : Optional.empty();
	}
}
