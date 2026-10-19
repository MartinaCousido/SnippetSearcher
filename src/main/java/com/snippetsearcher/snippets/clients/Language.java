package com.snippetsearcher.snippets.clients;

import java.util.List;

// Lo que todo lenguaje tiene que saber hacer. Snippet Service solo conoce esta
// interfaz, nunca un lenguaje concreto. Recibe el código, no el id del snippet.
// lint, format y evaluate se suman cuando se implementen.
public interface Language {

	// Revisa sintaxis y semántica sin ejecutar. Lista vacía = el código es válido.
	List<Diagnostic> validate(String code, String version);
}
