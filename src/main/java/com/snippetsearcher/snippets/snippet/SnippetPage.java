package com.snippetsearcher.snippets.snippet;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

// Una página del listado, con el formato de PaginatedSnippets de la UI.
public record SnippetPage(
		int page,
		@JsonProperty("page_size") int pageSize,
		long count,
		List<SnippetResponse> snippets) {
}
