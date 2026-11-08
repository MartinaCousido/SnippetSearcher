package com.snippetsearcher.snippets.testcase;

import java.util.List;

// El TestCase de la UI, más el resultado de su última corrida (US-16).
public record TestCaseResponse(String id, String name, List<String> input, List<String> output,
		TestResult lastResult) {

	static TestCaseResponse from(TestCase test) {
		return new TestCaseResponse(test.id().toString(), test.name(), test.inputs(), test.outputs(),
				test.lastResult());
	}
}
