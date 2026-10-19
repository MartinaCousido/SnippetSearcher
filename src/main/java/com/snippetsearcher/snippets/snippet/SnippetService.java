package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.security.CurrentUser;

@Service
public class SnippetService {

	private final LanguageCatalog languages;
	private final SnippetStorage storage;
	private final SnippetRepository repository;
	private final PermissionClient permissions;
	private final CurrentUser currentUser;
	private final TransactionTemplate transaction;

	public SnippetService(LanguageCatalog languages, SnippetStorage storage, SnippetRepository repository,
			PermissionClient permissions, CurrentUser currentUser, TransactionTemplate transaction) {
		this.languages = languages;
		this.storage = storage;
		this.repository = repository;
		this.permissions = permissions;
		this.currentUser = currentUser;
		this.transaction = transaction;
	}

	// Crea un snippet (US-1, US-3). Si el código no es válido, no se guarda nada.
	// Si algo falla después de guardar el archivo, se borra: no queda un
	// snippet a medias.
	public SnippetResponse create(CreateSnippetRequest request) {
		var language = languages.resolve(request.language(), request.version());
		var diagnostics = language.language().validate(request.content(), language.version());
		if (!diagnostics.isEmpty()) {
			throw new InvalidSnippetException(diagnostics);
		}

		var id = UUID.randomUUID();
		var contentKey = id.toString();
		var description = request.description() == null ? "" : request.description();
		storage.put(contentKey, request.content());
		try {
			var snippet = transaction.execute(status -> {
				var saved = repository.saveAndFlush(new Snippet(id, request.name(), description,
						language.name(), language.version(), contentKey));
				permissions.registerOwner(id);
				return saved;
			});
			return new SnippetResponse(id.toString(), snippet.name(), snippet.description(), snippet.language(),
					snippet.version(), language.extension(), request.content(), snippet.compliance(),
					currentUser.id());
		} catch (RuntimeException e) {
			deleteQuietly(contentKey, e);
			throw e;
		}
	}

	// Si borrar el archivo también falla, se responde con el error original: es
	// el que explica qué pasó.
	private void deleteQuietly(String contentKey, RuntimeException cause) {
		try {
			storage.delete(contentKey);
		} catch (RuntimeException deleteFailed) {
			cause.addSuppressed(deleteFailed);
		}
	}
}
