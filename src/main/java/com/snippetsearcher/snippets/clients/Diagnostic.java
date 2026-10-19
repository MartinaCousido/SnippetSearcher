package com.snippetsearcher.snippets.clients;

// Un problema en el código, igual para todos los lenguajes: la regla
// incumplida, un mensaje y dónde está. Líneas y columnas empiezan en 1.
public record Diagnostic(String rule, String message, Range range) {

	public record Range(Position start, Position end) {
	}

	public record Position(int line, int column) {
	}
}
