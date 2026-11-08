package com.snippetsearcher.snippets.rules;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Una regla que el usuario cambió. Las que no están en la tabla usan el valor
// por defecto del catálogo.
@Entity
@Table(name = "user_rules")
@IdClass(UserRule.Key.class)
public class UserRule {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Id
	private String kind;

	@Id
	private String name;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	// El valor como texto: el tipo lo dice el catálogo. null en las de sí o no.
	private String value;

	protected UserRule() {
		// Lo pide JPA.
	}

	public UserRule(String userId, RuleKind kind, String name, boolean active, String value) {
		this.userId = userId;
		this.kind = kind.value();
		this.name = name;
		this.active = active;
		this.value = value;
	}

	public String name() {
		return name;
	}

	public boolean active() {
		return active;
	}

	public String value() {
		return value;
	}

	public record Key(String userId, String kind, String name) implements Serializable {

		public Key() {
			this(null, null, null);
		}
	}
}
