package com.snippetsearcher.snippets.rules;

// Los dos grupos de reglas, con el valor que se guarda en la base y que usa
// la URL (/rules/lint y /rules/format).
public enum RuleKind {
	LINT("lint"),
	FORMAT("format");

	private final String value;

	RuleKind(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}
}
