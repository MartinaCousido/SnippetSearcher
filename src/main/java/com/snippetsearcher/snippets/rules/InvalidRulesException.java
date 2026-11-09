package com.snippetsearcher.snippets.rules;

public class InvalidRulesException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidRulesException(String message) {
		super(message);
	}

	public InvalidRulesException(String message, Throwable cause) {
		super(message, cause);
	}
}
