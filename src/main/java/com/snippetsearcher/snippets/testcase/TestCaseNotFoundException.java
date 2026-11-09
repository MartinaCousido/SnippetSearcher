package com.snippetsearcher.snippets.testcase;

import java.util.UUID;

public class TestCaseNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TestCaseNotFoundException(UUID id) {
		super("No existe el test " + id);
	}
}
