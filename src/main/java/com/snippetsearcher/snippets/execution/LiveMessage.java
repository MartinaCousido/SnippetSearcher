package com.snippetsearcher.snippets.execution;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.testcase.TestResult;

// Un mensaje que Snippet Service le manda a la UI por el WebSocket de
// ejecución en vivo: output { value }, input-request { prompt },
// error { diagnostic } y end { result? }. Ver "Ejecución en vivo" en
// Decisiones técnicas.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveMessage(String type, String value, String prompt, Diagnostic diagnostic, TestResult result) {

	static LiveMessage output(String value) {
		return new LiveMessage("output", value, null, null, null);
	}

	static LiveMessage inputRequest(String prompt) {
		return new LiveMessage("input-request", null, prompt, null, null);
	}

	static LiveMessage error(Diagnostic diagnostic) {
		return new LiveMessage("error", null, null, diagnostic, null);
	}

	// result es null en una ejecución interactiva y success o fail en un test.
	static LiveMessage end(TestResult result) {
		return new LiveMessage("end", null, null, null, result);
	}
}
