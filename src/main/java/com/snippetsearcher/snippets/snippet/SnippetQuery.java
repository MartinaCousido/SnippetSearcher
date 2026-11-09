package com.snippetsearcher.snippets.snippet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

// Qué snippets pide el listado (US-5) y en qué orden. Ver la fila de
// listSnippetDescriptors en Contrato con la UI. Los valores ya vienen
// validados por SnippetController: un valor que no existe es 400.
//   - name: contiene, sin distinguir mayúsculas. language y compliance: iguales.
//   - sort: name, language o compliance; order: asc o desc. Sin sort, por nombre.
//     A igual valor se desempata por nombre (y por id, para que una página no
//     cambie de un pedido al siguiente).
public record SnippetQuery(String name, String relation, String language, String compliance, String sort,
		String order) {

	static final String SORTS = "name|language|compliance";
	static final String ORDERS = "asc|desc";
	static final String COMPLIANCES = "compliant|not-compliant|pending|failed";
	static final String NAME = "name";

	// Los snippets con esos ids que cumplen los filtros.
	Specification<Snippet> among(Collection<UUID> ids) {
		return (root, query, builder) -> {
			var conditions = new ArrayList<Predicate>();
			conditions.add(root.get("id").in(ids));
			if (name != null && !name.isBlank()) {
				conditions.add(builder.like(builder.lower(root.get(NAME)),
						"%" + name.toLowerCase(Locale.ROOT) + "%"));
			}
			if (language != null && !language.isBlank()) {
				conditions.add(builder.equal(root.get("language"), language));
			}
			if (compliance != null && !compliance.isBlank()) {
				conditions.add(builder.equal(root.get("compliance"), Compliance.fromValue(compliance)));
			}
			return builder.and(conditions.toArray(Predicate[]::new));
		};
	}

	Sort sortOrder() {
		var field = sort == null || sort.isBlank() ? NAME : sort;
		var direction = "desc".equals(order) ? Sort.Direction.DESC : Sort.Direction.ASC;
		var sorted = Sort.by(direction, field);
		if (!NAME.equals(field)) {
			sorted = sorted.and(Sort.by(NAME));
		}
		return sorted.and(Sort.by("id"));
	}
}
