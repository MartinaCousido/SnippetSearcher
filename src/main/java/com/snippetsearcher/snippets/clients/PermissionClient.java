package com.snippetsearcher.snippets.clients;

import java.util.List;
import java.util.UUID;

// Lo que Snippet Service le pide a Permission Service.
public interface PermissionClient {

	// Registra como owner del snippet al usuario que hace el pedido.
	void registerOwner(UUID snippetId);

	// Los ids de los snippets a los que tiene acceso el usuario que pide:
	// relation es owner, shared o all.
	List<UUID> accessibleSnippets(String relation);
}
