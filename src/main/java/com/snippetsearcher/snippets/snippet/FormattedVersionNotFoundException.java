package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

// Todavía se está formateando, o el formateo no se pudo hacer (#23).
public class FormattedVersionNotFoundException extends RuntimeException {

	public FormattedVersionNotFoundException(UUID id) {
		super("Este snippet todavía no tiene versión formateada");
	}
}
