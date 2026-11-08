package com.snippetsearcher.snippets.testcase;

import java.util.List;

// Correr un test (testSnippet de la UI): puede ser uno guardado o uno que el
// usuario todavía está escribiendo. Sin inputs u outputs, listas vacías.
public record RunTestRequest(List<String> input, List<String> output) {

	public List<String> inputs() {
		return input == null ? List.of() : input;
	}

	public List<String> outputs() {
		return output == null ? List.of() : output;
	}
}
