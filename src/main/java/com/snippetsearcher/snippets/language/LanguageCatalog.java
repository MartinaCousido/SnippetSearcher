package com.snippetsearcher.snippets.language;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.snippetsearcher.snippets.clients.Language;

// Los lenguajes que soporta la plataforma, con sus versiones. Salen de
// application.properties (languages.<nombre>.*), y cada uno tiene un bean de
// Language con el mismo nombre. Sumar un lenguaje = una implementación de
// Language y unas líneas de configuración.
@Component
public class LanguageCatalog {

	private final Map<String, Config> configs;
	private final Map<String, Language> implementations;

	public LanguageCatalog(Environment environment, Map<String, Language> implementations) {
		this.configs = Binder.get(environment)
				.bind("languages", Bindable.mapOf(String.class, Config.class))
				.orElse(Map.of());
		this.implementations = implementations;
	}

	// Devuelve el lenguaje con la versión pedida, o con su versión por defecto
	// si no se pidió ninguna. Falla si el lenguaje o la versión no existen.
	public ResolvedLanguage resolve(String name, String version) {
		var config = configs.get(name);
		var implementation = implementations.get(name);
		if (config == null || implementation == null) {
			throw new UnsupportedLanguageException("Lenguaje no soportado: " + name);
		}
		var chosen = version == null || version.isBlank() ? config.defaultVersion() : version;
		if (!config.versions().contains(chosen)) {
			throw new UnsupportedLanguageException("Versión de " + name + " no soportada: " + chosen);
		}
		return new ResolvedLanguage(name, chosen, config.extension(), implementation);
	}

	public List<FileType> fileTypes() {
		return configs.entrySet().stream()
				.map(entry -> new FileType(entry.getKey(), entry.getValue().extension()))
				.toList();
	}

	public record Config(String extension, List<String> versions, String defaultVersion) {
	}

	public record ResolvedLanguage(String name, String version, String extension, Language language) {
	}

	// Lo que muestra la UI: el lenguaje y la extensión de sus archivos.
	public record FileType(String language, String extension) {
	}
}
