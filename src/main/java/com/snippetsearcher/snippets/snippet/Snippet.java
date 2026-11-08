package com.snippetsearcher.snippets.snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

import com.snippetsearcher.snippets.clients.Diagnostic;

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

	// Quién lo creó: su owner.
	@Column(nullable = false)
	private String author;

	// Suben cada vez que se pide un lint o un formateo nuevo (#23): un
	// resultado de una revisión vieja se descarta.
	@Column(name = "lint_revision", nullable = false)
	private int lintRevision;

	@Column(name = "format_revision", nullable = false)
	private int formatRevision;

	// Clave del archivo formateado en el Snippet Storage. null si todavía no
	// hay, o si no se pudo formatear.
	@Column(name = "formatted_key")
	private String formattedKey;

	// Las reglas de lint que incumple, del último lint (US-6). Se cargan con el
	// snippet; en el listado, las de toda la página en una sola consulta.
	@ElementCollection(fetch = FetchType.EAGER)
	@BatchSize(size = 100)
	@CollectionTable(name = "snippet_lint_violations", joinColumns = @JoinColumn(name = "snippet_id"))
	@OrderColumn(name = "position")
	private List<LintViolation> lintViolations = new ArrayList<>();

	protected Snippet() {
		// Lo pide JPA.
	}

	public Snippet(UUID id, String name, String description, String language, String version, String contentKey,
			String author) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.language = language;
		this.version = version;
		this.contentKey = contentKey;
		this.compliance = Compliance.PENDING;
		this.author = author;
	}

	// Cambia los metadatos al actualizar el snippet (US-2, US-4). El código
	// cambió, así que el estado de lint vuelve a PENDING.
	public void update(String name, String description, String language, String version) {
		this.name = name;
		this.description = description;
		this.language = language;
		this.version = version;
		this.compliance = Compliance.PENDING;
	}

	// Pide un lint nuevo: vuelve a PENDING y devuelve la revisión que tiene
	// que traer el resultado.
	public int requestLint() {
		this.compliance = Compliance.PENDING;
		this.lintRevision = this.lintRevision + 1;
		return lintRevision;
	}

	public int requestFormat() {
		this.formatRevision = this.formatRevision + 1;
		return formatRevision;
	}

	// Guarda el resultado de un lint, si es de la última revisión pedida.
	// Devuelve false si es viejo y se descartó.
	public boolean recordLint(int revision, List<Diagnostic> violations) {
		if (revision != lintRevision) {
			return false;
		}
		this.compliance = violations.isEmpty() ? Compliance.COMPLIANT : Compliance.NOT_COMPLIANT;
		this.lintViolations.clear();
		violations.forEach(violation -> this.lintViolations.add(LintViolation.from(violation)));
		return true;
	}

	// Se agotaron los intentos de lintear esta revisión.
	public boolean lintFailed(int revision) {
		if (revision != lintRevision) {
			return false;
		}
		this.compliance = Compliance.FAILED;
		this.lintViolations.clear();
		return true;
	}

	// Guarda dónde quedó la versión formateada, o null si no se pudo, si es de
	// la última revisión pedida.
	public boolean recordFormatted(int revision, String key) {
		if (revision != formatRevision) {
			return false;
		}
		this.formattedKey = key;
		return true;
	}

	public int lintRevision() {
		return lintRevision;
	}

	public int formatRevision() {
		return formatRevision;
	}

	public String formattedKey() {
		return formattedKey;
	}

	public List<Diagnostic> lintViolations() {
		return lintViolations.stream().map(LintViolation::toDiagnostic).toList();
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

	public String author() {
		return author;
	}
}
