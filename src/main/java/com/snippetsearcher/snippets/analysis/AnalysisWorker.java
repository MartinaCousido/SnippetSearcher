package com.snippetsearcher.snippets.analysis;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.language.LanguageCatalog.ResolvedLanguage;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.rules.RuleKind;
import com.snippetsearcher.snippets.rules.RuleService;
import com.snippetsearcher.snippets.snippet.InvalidSnippetException;
import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Lintea o formatea un snippet con las reglas de su owner y guarda el
// resultado. Lo llama el consumidor de la cola, una vez por mensaje.
@Service
public class AnalysisWorker {

	private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

	private final SnippetRepository snippets;
	private final SnippetStorage storage;
	private final LanguageCatalog languages;
	private final RuleService rules;
	private final ParserVersions versions;

	public AnalysisWorker(SnippetRepository snippets, SnippetStorage storage, LanguageCatalog languages,
			RuleService rules, ParserVersions versions) {
		this.snippets = snippets;
		this.storage = storage;
		this.languages = languages;
		this.rules = rules;
		this.versions = versions;
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
			lint(snippet, revision, language, code);
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
		log.info("formateo del snippet {} guardado", snippetId);
	}

	// Primero valida con el parser: con una versión nueva de la librería, un
	// snippet guardado puede dejar de ser válido. Si no lo es, queda
	// NOT_COMPLIANT con los errores de sintaxis, como reglas incumplidas (#93).
	// Si es válido, se lintea con las reglas del owner.
	private void lint(Snippet snippet, int revision, ResolvedLanguage language, String code) {
		var libraryVersion = versions.current(snippet.language());
		var errors = language.language().validate(code, language.version());
		var violations = errors.isEmpty()
				? language.language().lint(code, language.version(), rules.activeRules(snippet.author(), RuleKind.LINT))
				: errors;
		snippet.recordLint(revision, violations, libraryVersion);
		log.info("lint del snippet {} con la librería {}: {} problemas{}", snippet.id(), libraryVersion,
				violations.size(), errors.isEmpty() ? "" : " (ya no es válido)");
	}

	// Se agotaron los intentos: el lint queda en FAILED, y el snippet queda
	// sin versión formateada (ver Cola de mensajes en Decisiones técnicas).
	@Transactional
	public void giveUp(AnalysisTask task, UUID snippetId, int revision) {
		snippets.findById(snippetId).ifPresent(snippet -> {
			if (task == AnalysisTask.LINT) {
				snippet.lintFailed(revision, versions.current(snippet.language()));
			} else {
				snippet.recordFormatted(revision, null);
			}
		});
	}

	// La versión formateada es un segundo archivo; el original no se toca.
	public static String formattedKey(String contentKey) {
		return contentKey + ".formatted";
	}

	private static int currentRevision(AnalysisTask task, Snippet snippet) {
		return task == AnalysisTask.LINT ? snippet.lintRevision() : snippet.formatRevision();
	}
}
