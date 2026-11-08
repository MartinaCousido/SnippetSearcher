package com.snippetsearcher.snippets.snippet;

import java.util.List;

import com.snippetsearcher.snippets.clients.Diagnostic;

// El Snippet que espera la UI.
public record SnippetResponse(
		String id,
		String name,
		String description,
		String language,
		String version,
		String extension,
		String content,
		Compliance compliance,
		String author,
		// Las reglas de lint que incumple, del último lint (US-6).
		List<Diagnostic> lintViolations) {
}
