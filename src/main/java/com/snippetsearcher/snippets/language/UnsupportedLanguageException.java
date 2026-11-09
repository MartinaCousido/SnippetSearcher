package com.snippetsearcher.snippets.language;

// El lenguaje o la versión que pidieron no existe en la plataforma.
public class UnsupportedLanguageException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UnsupportedLanguageException(String message) {
		super(message);
	}
}
