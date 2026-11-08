package com.snippetsearcher.snippets.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

// Usuarios de prueba para MockMvc. jwt() pone el usuario como ya autenticado,
// sin llamar a Auth0: arma el token con el "sub" y el "scope" que se le piden.
public final class TestUsers {

	// Un usuario con el rol snippet-user: tiene los dos permisos.
	public static final String ANA = "auth0|ana";

	private TestUsers() {
	}

	public static JwtRequestPostProcessor ana() {
		return user(ANA, "read:snippets write:snippets");
	}

	public static JwtRequestPostProcessor user(String sub, String scope) {
		return jwt().jwt(token -> token.subject(sub).claim("scope", scope));
	}
}
