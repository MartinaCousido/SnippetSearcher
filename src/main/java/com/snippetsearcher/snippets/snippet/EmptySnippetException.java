package com.snippetsearcher.snippets.snippet;

// El snippet no tiene código, o solo tiene espacios.
public class EmptySnippetException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EmptySnippetException() {
		super("El código del snippet no puede estar vacío");
	}
}
