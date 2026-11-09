package com.snippetsearcher.snippets.analysis;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.snippet.Compliance;
import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Re-valida los snippets con cada versión nueva del parser (consigna, #93):
// al arrancar y cada 10 minutos le pregunta a cada lenguaje su versión, y pide
// el lint y el formateo de los snippets que se analizaron con otra. El
// servicio de lenguaje se despliega solo, sin reiniciar este: por eso se
// pregunta cada tanto. Como cada snippet guarda su versión, si el proceso se
// corta, la próxima vuelta sigue con los que faltan. Ver "Re-validar con cada
// versión del parser" en Decisiones técnicas.
@Component
@ConditionalOnProperty(name = "analysis.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class ParserVersionCheck {

	private static final Logger log = LoggerFactory.getLogger(ParserVersionCheck.class);

	private final LanguageCatalog languages;
	private final ParserVersions versions;
	private final SnippetRepository snippets;
	private final AnalysisRequests requests;

	public ParserVersionCheck(LanguageCatalog languages, ParserVersions versions, SnippetRepository snippets,
			AnalysisRequests requests) {
		this.languages = languages;
		this.versions = versions;
		this.snippets = snippets;
		this.requests = requests;
	}

	@Scheduled(initialDelayString = "${analysis.parser-check-initial-delay}",
			fixedDelayString = "${analysis.parser-check-interval}")
	@Transactional
	public void check() {
		for (var type : languages.fileTypes()) {
			var language = type.language();
			var version = versions.current(language);
			if (version == null) {
				continue;
			}
			var ids = snippets.findIdsAnalyzedWithOtherVersion(language, version, Compliance.PENDING);
			if (!ids.isEmpty()) {
				log.info("{} {}: se vuelven a analizar {} snippets analizados con otra versión", language, version,
						ids.size());
				requests.request(ids, EnumSet.allOf(AnalysisTask.class));
			}
		}
	}
}
