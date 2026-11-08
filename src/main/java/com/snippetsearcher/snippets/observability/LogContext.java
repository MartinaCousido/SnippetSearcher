package com.snippetsearcher.snippets.observability;

// Los nombres del contexto de los logs (MDC). Son los atributos por los que se
// filtra en New Relic.
public final class LogContext {

	public static final String REQUEST_ID = "requestId";
	public static final String USER = "user";
	public static final String SERVICE = "service";
	public static final String SNIPPET_ID = "snippetId";
	public static final String SERVICE_NAME = "snippet-service";

	private LogContext() {
	}
}
