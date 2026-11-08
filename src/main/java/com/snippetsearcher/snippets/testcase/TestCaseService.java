package com.snippetsearcher.snippets.testcase;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetAccessDeniedException;
import com.snippetsearcher.snippets.snippet.SnippetNotFoundException;
import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Tests de un snippet (US-8) y su corrida automática al actualizarlo (US-16).
// Los ven y los corren el owner y los usuarios con acceso compartido; solo el
// owner los crea, cambia y borra.
@Service
public class TestCaseService {

	private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

	private final TestCaseRepository tests;
	private final SnippetRepository snippets;
	private final PermissionClient permissions;
	private final SnippetStorage storage;
	private final LanguageCatalog languages;
	private final TestRunner runner;

	public TestCaseService(TestCaseRepository tests, SnippetRepository snippets, PermissionClient permissions,
			SnippetStorage storage, LanguageCatalog languages, TestRunner runner) {
		this.tests = tests;
		this.snippets = snippets;
		this.permissions = permissions;
		this.storage = storage;
		this.languages = languages;
		this.runner = runner;
	}

	public List<TestCaseResponse> list(UUID snippetId) {
		readable(snippetId);
		return tests.findBySnippetIdOrderByCreatedAtAscIdAsc(snippetId).stream()
				.map(TestCaseResponse::from)
				.toList();
	}

	// Con id, cambia ese test; sin id, crea uno nuevo. Un test que no pasa se
	// guarda igual (US-8).
	@Transactional
	public TestCaseResponse save(UUID snippetId, TestCaseRequest request) {
		writable(snippetId);
		TestCase test;
		if (request.id() == null) {
			test = new TestCase(UUID.randomUUID(), snippetId, request.name(), request.inputs(), request.outputs());
		} else {
			test = tests.findByIdAndSnippetId(request.id(), snippetId)
					.orElseThrow(() -> new TestCaseNotFoundException(request.id()));
			test.change(request.name(), request.inputs(), request.outputs());
		}
		return TestCaseResponse.from(tests.save(test));
	}

	@Transactional
	public void delete(UUID snippetId, UUID testId) {
		writable(snippetId);
		var test = tests.findByIdAndSnippetId(testId, snippetId)
				.orElseThrow(() -> new TestCaseNotFoundException(testId));
		tests.delete(test);
	}

	// Corre un test con el código guardado del snippet. No guarda el
	// resultado: el test puede no estar guardado todavía.
	public RunTestResponse run(UUID snippetId, RunTestRequest request) {
		var snippet = readable(snippetId);
		var language = languages.resolve(snippet.language(), snippet.version());
		var code = storage.get(snippet.contentKey()).orElse("");
		return new RunTestResponse(runner.run(language, code, request.inputs(), request.outputs()));
	}

	// Corre todos los tests de un snippet recién actualizado y guarda el
	// resultado de cada uno (US-16). Nunca falla: el update ya se guardó, y
	// la US-16 pide que el resultado no lo bloquee ni lo revierta. Si un test
	// no se puede correr, queda sin resultado.
	@Transactional
	public void runAll(Snippet snippet, String code) {
		var language = languages.resolve(snippet.language(), snippet.version());
		for (var test : tests.findBySnippetIdOrderByCreatedAtAscIdAsc(snippet.id())) {
			try {
				test.recordResult(runner.run(language, code, test.inputs(), test.outputs()));
			} catch (RuntimeException e) {
				log.warn("No se pudo correr el test {} del snippet {}", test.id(), snippet.id(), e);
				test.recordResult(null);
			}
		}
	}

	// Primero se busca el snippet: si no existe es 404, no 403.
	private Snippet readable(UUID snippetId) {
		var snippet = snippets.findById(snippetId).orElseThrow(() -> new SnippetNotFoundException(snippetId));
		var access = permissions.access(snippetId);
		if (access == null || !access.canRead()) {
			throw new SnippetAccessDeniedException(snippetId);
		}
		return snippet;
	}

	private void writable(UUID snippetId) {
		snippets.findById(snippetId).orElseThrow(() -> new SnippetNotFoundException(snippetId));
		var access = permissions.access(snippetId);
		if (access == null || !access.canWrite()) {
			throw new SnippetAccessDeniedException(snippetId);
		}
	}
}
