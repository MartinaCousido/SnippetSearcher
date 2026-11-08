package com.snippetsearcher.snippets.observability;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

// Loguea cada llamada a otro servicio: método, host y ruta (sin la query, que
// puede traer emails), qué respondió y cuánto tardó. Nunca el cuerpo ni los
// headers, que llevan tokens. Además le pasa el requestId del pedido
// (X-Request-Id), para que sus logs lleven el mismo.
public class OutboundCallLogger implements ClientHttpRequestInterceptor {

	private static final Logger log = LoggerFactory.getLogger(OutboundCallLogger.class);

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		var target = request.getURI().getHost() + request.getURI().getPath();
		var requestId = MDC.get(LogContext.REQUEST_ID);
		if (requestId != null && !request.getHeaders().containsHeader(RequestContextFilter.REQUEST_ID_HEADER)) {
			request.getHeaders().set(RequestContextFilter.REQUEST_ID_HEADER, requestId);
		}
		long start = System.nanoTime();
		try {
			var response = execution.execute(request, body);
			log.info("llamada {} {} → {} en {} ms", request.getMethod(), target, response.getStatusCode().value(),
					elapsedMs(start));
			return response;
		} catch (IOException | RuntimeException e) {
			log.warn("llamada {} {} → falló en {} ms: {}", request.getMethod(), target, elapsedMs(start),
					e.getMessage());
			throw e;
		}
	}

	private static long elapsedMs(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}
}
