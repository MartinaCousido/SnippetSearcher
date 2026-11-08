package com.snippetsearcher.snippets.testcase;

import java.util.UUID;

public class TestCaseNotFoundException extends RuntimeException {

	public TestCaseNotFoundException(UUID id) {
		super("No existe el test " + id);
	}
}
