package com.snippetsearcher.snippets.testcase;

import com.fasterxml.jackson.annotation.JsonValue;

// Resultado de correr un test, con los valores que usa la UI.
public enum TestResult {
	SUCCESS("success"),
	FAIL("fail");

	private final String value;

	TestResult(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	public static TestResult fromValue(String value) {
		for (var result : values()) {
			if (result.value.equals(value)) {
				return result;
			}
		}
		throw new IllegalArgumentException("Resultado de test desconocido: " + value);
	}
}
