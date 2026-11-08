package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

public class SnippetNotFoundException extends RuntimeException {

	public SnippetNotFoundException(UUID id) {
		super("No existe el snippet " + id);
	}
}
