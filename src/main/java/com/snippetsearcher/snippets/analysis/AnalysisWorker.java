package com.snippetsearcher.snippets.analysis;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.rules.RuleKind;
import com.snippetsearcher.snippets.rules.RuleService;
import com.snippetsearcher.snippets.snippet.InvalidSnippetException;
import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Lintea o formatea un snippet con las reglas de su owner y guarda el
// resultado. Lo llama el consumidor de la cola, una vez por mensaje.
@Service
public class AnalysisWorker {

	private final SnippetRepository snippets;
	private final SnippetStorage storage;
	private final LanguageCatalog languages;
	private final RuleService rules;

	public AnalysisWorker(SnippetRepository snippets, SnippetStorage storage, LanguageCatalog languages,
			RuleService rules) {
		this.snippets = snippets;
		this.storage = storage;
		this.languages = languages;
		this.rules = rules;
	}

	// Si termina sin error, el mensaje se confirma. Si falla, queda pendiente
	// y se reintenta. Un snippet que ya no existe, o una revisión vieja, no se
	// procesa: no hay nada que hacer.
	@Transactional
	public void process(AnalysisTask task, UUID snippetId, int revision) {
		var snippet = snippets.findById(snippetId).orElse(null);
		if (snippet == null || revision != currentRevision(task, snippet)) {
			return;
		}
		var language = languages.resolve(snippet.language(), snippet.version());
		var code = storage.get(snippet.contentKey()).orElse("");
		if (task == AnalysisTask.LINT) {
			var violations = language.language().lint(code, language.version(),
					rules.activeRules(snippet.author(), RuleKind.LINT));
			snippet.recordLint(revision, violations);
			return;
		}
		var formatted = language.language().format(code, language.version(),
				rules.activeRules(snippet.author(), RuleKind.FORMAT));
		if (formatted.failed()) {
			// Se reintenta como cualquier otro fallo.
			throw new InvalidSnippetException(formatted.diagnostics());
		}
		var key = formattedKey(snippet.contentKey());
		storage.put(key, formatted.content());
		snippet.recordFormatted(revision, key);
	}

	// Se agotaron los intentos: el lint queda en FAILED, y el snippet queda
	// sin versión formateada (ver Cola de mensajes en Decisiones técnicas).
	@Transactional
	public void giveUp(AnalysisTask task, UUID snippetId, int revision) {
		snippets.findById(snippetId).ifPresent(snippet -> {
			if (task == AnalysisTask.LINT) {
				snippet.lintFailed(revision);
			} else {
				snippet.recordFormatted(revision, null);
			}
		});
	}

	// La versión formateada es un segundo archivo; el original no se toca.
	public static String formattedKey(String contentKey) {
		return contentKey + ".formatted";
	}

	private static int currentRevision(AnalysisTask task, com.snippetsearcher.snippets.snippet.Snippet snippet) {
		return task == AnalysisTask.LINT ? snippet.lintRevision() : snippet.formatRevision();
	}
}
