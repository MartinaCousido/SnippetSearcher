package com.snippetsearcher.snippets.testcase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Un test de un snippet (US-8): inputs en el orden de los readInput y outputs
// esperados en el orden de los println. Puede fallar: se guarda igual.
@Entity
@Table(name = "test_cases")
public class TestCase {

	@Id
	private UUID id;

	@Column(name = "snippet_id", nullable = false)
	private UUID snippetId;

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> inputs;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> outputs;

	// Resultado de la última corrida automática (US-16). null: no se corrió.
	@Column(name = "last_result")
	private TestResult lastResult;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TestCase() {
		// Lo pide JPA.
	}

	public TestCase(UUID id, UUID snippetId, String name, List<String> inputs, List<String> outputs) {
		this.id = id;
		this.snippetId = snippetId;
		this.createdAt = Instant.now();
		apply(name, inputs, outputs);
	}

	// Cambiar el test borra su último resultado: era de otros inputs u outputs.
	public void change(String name, List<String> inputs, List<String> outputs) {
		apply(name, inputs, outputs);
	}

	// El constructor no llama a change(): JPA necesita que la clase y sus métodos
	// se puedan sobrescribir, y un constructor no debe llamar a esos métodos.
	private void apply(String name, List<String> inputs, List<String> outputs) {
		this.name = name;
		this.inputs = new ArrayList<>(inputs);
		this.outputs = new ArrayList<>(outputs);
		this.lastResult = null;
	}

	public void recordResult(TestResult result) {
		this.lastResult = result;
	}

	public UUID id() {
		return id;
	}

	public UUID snippetId() {
		return snippetId;
	}

	public String name() {
		return name;
	}

	public List<String> inputs() {
		return List.copyOf(inputs);
	}

	public List<String> outputs() {
		return List.copyOf(outputs);
	}

	public TestResult lastResult() {
		return lastResult;
	}
}
