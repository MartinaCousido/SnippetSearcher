package com.snippetsearcher.snippets.execution;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.snippetsearcher.snippets.observability.LogContext;

// WebSocket /snippets/{id}/execute (US-9, US-10). Desde el navegador se aceptan
// el mismo origin y los de CORS_ALLOWED_ORIGINS, como en el resto de la API
// (ver CORS en el README).
@Configuration
@EnableWebSocket
public class LiveExecutionConfig implements WebSocketConfigurer {

	private final LiveExecutionHandler handler;
	private final String[] allowedOrigins;

	public LiveExecutionConfig(LiveExecutionHandler handler,
			@Value("${cors.allowed-origins:}") String allowedOrigins) {
		this.handler = handler;
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
				.filter(origin -> !origin.isEmpty()).toArray(String[]::new);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		var registration = registry.addHandler(handler, "/snippets/*/execute")
				.addInterceptors(new RequestIdInterceptor());
		if (allowedOrigins.length > 0) {
			registration.setAllowedOrigins(allowedOrigins);
		}
	}

	// Los mensajes llegan en otros hilos, sin el MDC del handshake: el requestId
	// que puso RequestContextFilter se guarda en la sesión.
	private static final class RequestIdInterceptor implements HandshakeInterceptor {

		@Override
		public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
				Map<String, Object> attributes) {
			var requestId = MDC.get(LogContext.REQUEST_ID);
			if (requestId != null) {
				attributes.put(LogContext.REQUEST_ID, requestId);
			}
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
				Exception exception) {
			// Nada que hacer después del handshake.
		}
	}
}
