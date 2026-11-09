package com.snippetsearcher.snippets.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.snippetsearcher.snippets.language.LanguageCatalog;

// La versión de la librería de cada lenguaje (GET /version), para guardar con
// qué versión se analizó cada snippet (#93). Si el servicio del lenguaje no la
// sabe o no responde, es null: el snippet se analiza igual, y la próxima
// revisión lo vuelve a pedir.
@Component
public class ParserVersions {

	private static final Logger log = LoggerFactory.getLogger(ParserVersions.class);

	private final LanguageCatalog languages;

	public ParserVersions(LanguageCatalog languages) {
		this.languages = languages;
	}

	public String current(String language) {
		try {
			return languages.resolve(language, null).language().libraryVersion();
		} catch (RuntimeException e) {
			log.warn("No se pudo saber la versión de la librería de {}", language, e);
			return null;
		}
	}
}
