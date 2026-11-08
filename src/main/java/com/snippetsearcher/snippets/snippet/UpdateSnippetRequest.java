package com.snippetsearcher.snippets.snippet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Lo que manda la UI al actualizar un snippet. La UI de la cátedra manda solo
// content; los metadatos son opcionales (la US-2 permite cambiarlos) y el que
// no viene queda como estaba. name, si viene, no puede quedar vacío.
public record UpdateSnippetRequest(
		@NotNull String content,
		@Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "no puede estar vacío") String name,
		String description,
		String language,
		String version) {
}
