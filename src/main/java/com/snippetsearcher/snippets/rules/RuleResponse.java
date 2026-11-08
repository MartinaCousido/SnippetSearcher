package com.snippetsearcher.snippets.rules;

// El Rule de la UI: id y name son el nombre de la regla en la librería. value
// es null en las de sí o no, un texto o un número en las demás.
public record RuleResponse(String id, String name, boolean isActive, Object value) {
}
