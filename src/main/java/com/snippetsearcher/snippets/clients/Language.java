package com.snippetsearcher.snippets.clients;

import java.util.List;

// Lo que todo lenguaje tiene que saber hacer. Snippet Service solo conoce esta
// interfaz, nunca un lenguaje concreto. Recibe el código, no el id del snippet.
// lint y format se suman cuando se implementen.
public interface Language {

	// Revisa sintaxis y semántica sin ejecutar. Lista vacía = el código es válido.
	List<Diagnostic> validate(String code, String version);

	// Ejecuta el código. Cada readInput toma el siguiente de inputs. Devuelve
	// lo que imprimió cada println, en orden; si la ejecución falló, el error
	// viene en diagnostics.
	Evaluation evaluate(String code, String version, List<String> inputs);

	record Evaluation(List<String> outputs, List<Diagnostic> diagnostics) {

		public boolean failed() {
			return !diagnostics.isEmpty();
		}
	}
}
