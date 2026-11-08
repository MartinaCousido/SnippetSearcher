package com.snippetsearcher.snippets.snippet;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;

// Una regla de lint que el snippet incumple (US-6): una fila de
// snippet_lint_violations.
@Embeddable
public class LintViolation {

	@Column(nullable = false)
	private String rule;

	@Column(nullable = false)
	private String message;

	@Column(name = "start_line", nullable = false)
	private int startLine;

	@Column(name = "start_column", nullable = false)
	private int startColumn;

	@Column(name = "end_line", nullable = false)
	private int endLine;

	@Column(name = "end_column", nullable = false)
	private int endColumn;

	protected LintViolation() {
		// Lo pide JPA.
	}

	static LintViolation from(Diagnostic diagnostic) {
		var violation = new LintViolation();
		violation.rule = diagnostic.rule();
		violation.message = diagnostic.message();
		violation.startLine = diagnostic.range().start().line();
		violation.startColumn = diagnostic.range().start().column();
		violation.endLine = diagnostic.range().end().line();
		violation.endColumn = diagnostic.range().end().column();
		return violation;
	}

	Diagnostic toDiagnostic() {
		return new Diagnostic(rule, message,
				new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)));
	}
}
