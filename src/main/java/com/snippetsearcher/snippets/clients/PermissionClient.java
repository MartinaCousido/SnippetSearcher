package com.snippetsearcher.snippets.clients;

import java.util.UUID;

// Lo que Snippet Service le pide a Permission Service.
public interface PermissionClient {

	// Registra como owner del snippet al usuario que hace el pedido.
	void registerOwner(UUID snippetId);
}
