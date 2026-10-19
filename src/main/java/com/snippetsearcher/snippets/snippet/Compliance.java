package com.snippetsearcher.snippets.snippet;

import com.fasterxml.jackson.annotation.JsonValue;

// Estado de lint de un snippet, con los valores que usa la UI.
public enum Compliance {
	PENDING("pending"),
	FAILED("failed"),
	NOT_COMPLIANT("not-compliant"),
	COMPLIANT("compliant");

	private final String value;

	Compliance(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	public static Compliance fromValue(String value) {
		for (var compliance : values()) {
			if (compliance.value.equals(value)) {
				return compliance;
			}
		}
		throw new IllegalArgumentException("Estado de lint desconocido: " + value);
	}
}
