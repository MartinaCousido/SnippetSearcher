package com.snippetsearcher.snippets.snippet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Lo que manda la UI al crear un snippet. description y version son
// opcionales: la UI de la cátedra no los manda. extension se ignora: sale de
// la configuración del lenguaje.
public record CreateSnippetRequest(
		@NotBlank @Size(max = 255) String name,
		String description,
		@NotBlank String language,
		String version,
		@NotNull String content) {
}
