package com.snippetsearcher.snippets.snippet;

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
		String author) {
}
