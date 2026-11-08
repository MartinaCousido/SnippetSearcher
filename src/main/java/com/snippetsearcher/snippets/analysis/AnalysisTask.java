package com.snippetsearcher.snippets.analysis;

// Las dos tareas en segundo plano, cada una con su stream de Redis (ver Cola de
// mensajes en Decisiones técnicas).
public enum AnalysisTask {
	LINT("snippets:lint"),
	FORMAT("snippets:format");

	private final String stream;

	AnalysisTask(String stream) {
		this.stream = stream;
	}

	public String stream() {
		return stream;
	}
}
