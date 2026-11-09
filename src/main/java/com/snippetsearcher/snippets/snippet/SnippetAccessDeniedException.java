package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

public class SnippetAccessDeniedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SnippetAccessDeniedException(UUID id) {
		super("No tenés acceso al snippet " + id);
	}
}
