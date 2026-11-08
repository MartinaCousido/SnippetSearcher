package com.snippetsearcher.snippets.users;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

// Una página de usuarios, con el formato de PaginatedUsers de la UI.
public record UserPage(
		int page,
		@JsonProperty("page_size") int pageSize,
		long count,
		List<User> users) {

	// El User de la UI: name es lo que se muestra al elegir con quién compartir.
	// Es el email. id es el que se manda al compartir.
	public record User(String id, String name) {
	}
}
