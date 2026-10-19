package com.snippetsearcher.snippets.security;

import org.springframework.stereotype.Component;

// Dice quién es el usuario que hace el pedido. Es el único lugar del servicio
// que lo sabe: el userId nunca se toma de un dato que manda el cliente.
// Mientras no esté Auth0 es siempre el usuario fijo dev-user (decisión
// temporal, ver Decisiones técnicas). Con Auth0, va a sacarlo del JWT.
@Component
public class CurrentUser {

	public static final String DEV_USER = "dev-user";

	public String id() {
		return DEV_USER;
	}
}
