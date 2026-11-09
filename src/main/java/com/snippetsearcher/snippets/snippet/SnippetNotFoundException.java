package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

public class SnippetNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SnippetNotFoundException(UUID id) {
		super("No existe el snippet " + id);
	}
}
