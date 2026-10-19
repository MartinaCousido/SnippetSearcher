package com.snippetsearcher.snippets.language;

// El lenguaje o la versión que pidieron no existe en la plataforma.
public class UnsupportedLanguageException extends RuntimeException {

	public UnsupportedLanguageException(String message) {
		super(message);
	}
}
