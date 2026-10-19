package com.snippetsearcher.snippets.clients;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// PermissionClient por HTTP. No manda el userId: Permission Service sabe quién
// pide. Mientras no esté Auth0 los dos usan dev-user; con Auth0, acá se va a
// reenviar el token del usuario.
@Component
public class PermissionServiceClient implements PermissionClient {

	private final RestClient client;

	public PermissionServiceClient(RestClient.Builder builder, @Value("${permission-service.url}") String baseUrl) {
		this.client = builder.baseUrl(baseUrl).build();
	}

	@Override
	public void registerOwner(UUID snippetId) {
		client.post()
				.uri("/permissions/{snippetId}/owner", snippetId)
				.retrieve()
				.toBodilessEntity();
	}
}
