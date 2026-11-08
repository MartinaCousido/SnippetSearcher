package com.snippetsearcher.snippets.clients;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

// Lo que todo lenguaje tiene que saber hacer. Snippet Service solo conoce esta
// interfaz, nunca un lenguaje concreto. Recibe el código, no el id del snippet.
public interface Language {

	// Revisa sintaxis y semántica sin ejecutar. Lista vacía = el código es válido.
	List<Diagnostic> validate(String code, String version);

	// Ejecuta el código. Cada readInput toma el siguiente de inputs. Devuelve
	// lo que imprimió cada println, en orden; si la ejecución falló, el error
	// viene en diagnostics.
	Evaluation evaluate(String code, String version, List<String> inputs);

	// Las reglas de lint y de formateo que existen, con su valor por defecto.
	RuleCatalog rules();

	// Lintea con las reglas activas (nombre → valor; true en las de sí o no).
	// Devuelve cada regla incumplida.
	List<Diagnostic> lint(String code, String version, Map<String, Object> rules);

	// Formatea con las reglas activas. Si falla, content es el código sin
	// cambios y el error viene en diagnostics.
	Formatted format(String code, String version, Map<String, Object> rules);

	record RuleCatalog(List<CatalogRule> lint, List<CatalogRule> format) {
	}

	// type: boolean, text (con options) o number. opposite: la regla contraria.
	record CatalogRule(String name, String type, List<String> options, String opposite,
			@JsonProperty("default") Default defaultValue) {

		public record Default(boolean isActive, Object value) {
		}
	}

	record Formatted(String content, List<Diagnostic> diagnostics) {

		public boolean failed() {
			return !diagnostics.isEmpty();
		}
	}

	record Evaluation(List<String> outputs, List<Diagnostic> diagnostics) {

		public boolean failed() {
			return !diagnostics.isEmpty();
		}
	}
}
