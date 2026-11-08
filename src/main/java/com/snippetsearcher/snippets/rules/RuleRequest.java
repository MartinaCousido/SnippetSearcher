package com.snippetsearcher.snippets.rules;

// Una regla como la manda la UI al guardar (Rule de la UI). value puede venir
// como texto o como número.
public record RuleRequest(String id, String name, boolean isActive, Object value) {

	// La UI manda id y name iguales; alcanza con uno.
	public String ruleName() {
		return name != null ? name : id;
	}
}
