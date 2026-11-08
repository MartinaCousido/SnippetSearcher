package com.snippetsearcher.snippets.observability;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.snippetsearcher.snippets.security.CurrentUser;

// Carga el contexto de cada pedido en los logs (MDC): requestId, usuario y
// servicio. Todas las líneas que se escriben durante el pedido lo llevan, y el
// agente de New Relic lo manda como atributos. Ver Observabilidad (New Relic)
// en Decisiones técnicas.
public class RequestContextFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = "X-Request-Id";

	private final CurrentUser currentUser;

	public RequestContextFilter(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Si llega un requestId (por ejemplo, de un synthetic), se respeta.
		var requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
			requestId = UUID.randomUUID().toString();
		}
		MDC.put(LogContext.REQUEST_ID, requestId);
		MDC.put(LogContext.SERVICE, LogContext.SERVICE_NAME);
		currentUser.token().ifPresent(token -> MDC.put(LogContext.USER, currentUser.id()));
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(LogContext.REQUEST_ID);
			MDC.remove(LogContext.SERVICE);
			MDC.remove(LogContext.USER);
		}
	}
}
