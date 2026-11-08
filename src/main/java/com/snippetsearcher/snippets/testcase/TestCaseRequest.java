package com.snippetsearcher.snippets.testcase;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

// Lo que manda la UI al guardar un test (postTestCase). Con id, cambia ese
// test; sin id, crea uno nuevo. Sin inputs u outputs, listas vacías.
public record TestCaseRequest(UUID id, @NotBlank String name, List<String> input, List<String> output) {

	public List<String> inputs() {
		return input == null ? List.of() : input;
	}

	public List<String> outputs() {
		return output == null ? List.of() : output;
	}
}
