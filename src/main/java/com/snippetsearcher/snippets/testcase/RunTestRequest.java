package com.snippetsearcher.snippets.testcase;

import java.util.List;
import java.util.UUID;

// Correr un test (testSnippet de la UI): puede ser uno guardado (con su id) o
// uno que el usuario todavía está escribiendo. Sin inputs u outputs, listas
// vacías.
public record RunTestRequest(UUID id, List<String> input, List<String> output) {

	public List<String> inputs() {
		return input == null ? List.of() : input;
	}

	public List<String> outputs() {
		return output == null ? List.of() : output;
	}
}
