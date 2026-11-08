package com.snippetsearcher.snippets.snippet;

import jakarta.validation.constraints.NotNull;

// formatSnippet() de la UI: el código que está en el editor, guardado o no.
public record FormatRequest(@NotNull String content) {
}
