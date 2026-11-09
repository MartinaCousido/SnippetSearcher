package com.snippetsearcher.snippets.snippet;

import java.util.List;

import com.snippetsearcher.snippets.clients.Diagnostic;

// El código no es válido para su lenguaje y versión.
public class InvalidSnippetException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<Diagnostic> diagnostics;

	public InvalidSnippetException(List<Diagnostic> diagnostics) {
		super("El código no es válido");
		this.diagnostics = diagnostics;
	}

	public List<Diagnostic> diagnostics() {
		return diagnostics;
	}
}
