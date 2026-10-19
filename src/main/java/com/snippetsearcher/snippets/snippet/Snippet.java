package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Metadatos de un snippet: una fila de la tabla snippets. El código no está
// acá: vive en el Snippet Storage, bajo la clave contentKey.
@Entity
@Table(name = "snippets")
public class Snippet {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private String language;

	@Column(nullable = false)
	private String version;

	@Column(name = "content_key", nullable = false, unique = true)
	private String contentKey;

	@Column(nullable = false)
	private Compliance compliance;

	protected Snippet() {
		// Lo pide JPA.
	}

	public Snippet(UUID id, String name, String description, String language, String version, String contentKey) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.language = language;
		this.version = version;
		this.contentKey = contentKey;
		this.compliance = Compliance.PENDING;
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public String language() {
		return language;
	}

	public String version() {
		return version;
	}

	public String contentKey() {
		return contentKey;
	}

	public Compliance compliance() {
		return compliance;
	}
}
