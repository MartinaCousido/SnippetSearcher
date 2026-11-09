package com.snippetsearcher.snippets.snippet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

// Qué snippets pide el listado (US-5) y en qué orden. Ver la fila de
// listSnippetDescriptors en Contrato con la UI. Los valores ya vienen
// validados por SnippetController: un valor que no existe es 400.
//   - name: contiene, sin distinguir mayúsculas. language y compliance: iguales.
//   - sort: name, language o compliance; order: asc o desc. Sin sort, por nombre.
//   - La validez no se ordena por orden alfabético sino de la mejor a la peor:
//     compliant, not-compliant, pending (todavía sin resultado) y failed (no se
//     pudo analizar).
//   - A igual valor se desempata por nombre, en el mismo sentido, y por id,
//     para que una página no cambie de un pedido al siguiente.
public record SnippetQuery(String name, String relation, String language, String compliance, String sort,
		String order) {

	static final String SORTS = "name|language|compliance";
	static final String ORDERS = "asc|desc";
	static final String COMPLIANCES = "compliant|not-compliant|pending|failed";
	private static final String NAME = "name";
	private static final String COMPLIANCE = "compliance";

	// Los snippets con esos ids que cumplen los filtros, en el orden pedido. El
	// orden se arma acá y no con Sort porque la validez necesita un CASE. La
	// consulta que cuenta el total no lleva orden.
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
				conditions.add(builder.equal(root.get(COMPLIANCE), Compliance.fromValue(compliance)));
			}
			if (query != null && !Long.class.equals(query.getResultType())) {
				query.orderBy(orders(root, builder));
			}
			return builder.and(conditions.toArray(Predicate[]::new));
		};
	}

	private List<Order> orders(Root<Snippet> root, CriteriaBuilder builder) {
		var descending = "desc".equals(order);
		var field = sort == null || sort.isBlank() ? NAME : sort;
		var orders = new ArrayList<Order>();
		if (COMPLIANCE.equals(field)) {
			orders.add(direction(builder, complianceRank(root, builder), descending));
		} else if (!NAME.equals(field)) {
			orders.add(direction(builder, root.get(field), descending));
		}
		orders.add(direction(builder, root.get(NAME), descending));
		orders.add(builder.asc(root.get("id")));
		return orders;
	}

	private static Expression<Integer> complianceRank(Root<Snippet> root, CriteriaBuilder builder) {
		var value = root.<Compliance>get(COMPLIANCE);
		return builder.<Integer>selectCase()
				.when(builder.equal(value, Compliance.COMPLIANT), 0)
				.when(builder.equal(value, Compliance.NOT_COMPLIANT), 1)
				.when(builder.equal(value, Compliance.PENDING), 2)
				.otherwise(3);
	}

	private static Order direction(CriteriaBuilder builder, Expression<?> expression, boolean descending) {
		return descending ? builder.desc(expression) : builder.asc(expression);
	}
}
