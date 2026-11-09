package com.snippetsearcher.snippets.execution;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.snippetsearcher.snippets.clients.Language.LiveExecution;
import com.snippetsearcher.snippets.observability.LogContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// WebSocket /snippets/{id}/execute, el que abre la UI. Cada conexión hace una
// sola ejecución. El primer mensaje (start) trae el token y, para correr un
// test, su testId; después pueden llegar inputs. Ver "Ejecución en vivo" en
// Decisiones técnicas.
@Component
public class LiveExecutionHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(LiveExecutionHandler.class);
	private static final String EXECUTION = "execution";
	private static final String SINK = "sink";
	private static final int SEND_TIME_LIMIT_MS = 10_000;
	private static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

	private final LiveExecutionService executions;
	private final JsonMapper json;

	public LiveExecutionHandler(LiveExecutionService executions, JsonMapper json) {
		this.executions = executions;
		this.json = json;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		var sender = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
		session.getAttributes().put(SINK, new SessionSink(sender, json));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
		var requestId = (String) session.getAttributes().get(LogContext.REQUEST_ID);
		if (requestId != null) {
			MDC.put(LogContext.REQUEST_ID, requestId);
		}
		try {
			handle(session, message);
		} finally {
			MDC.remove(LogContext.REQUEST_ID);
		}
	}

	private void handle(WebSocketSession session, TextMessage message) throws IOException {
		JsonNode received;
		try {
			received = json.readTree(message.getPayload());
		} catch (JacksonException e) {
			reject(session, CloseStatus.BAD_DATA.withReason("El mensaje no es JSON válido"));
			return;
		}
		var execution = (LiveExecution) session.getAttributes().get(EXECUTION);
		switch (received.path("type").asString("")) {
			case "start" -> start(session, execution, received);
			case "input" -> {
				if (execution == null) {
					reject(session, CloseStatus.BAD_DATA.withReason("Primero hay que mandar start"));
					return;
				}
				execution.input(received.path("value").asString(""));
			}
			default -> reject(session, CloseStatus.BAD_DATA.withReason("Tipo de mensaje desconocido"));
		}
	}

	private void start(WebSocketSession session, LiveExecution current, JsonNode start) throws IOException {
		if (current != null || session.getAttributes().containsKey(EXECUTION)) {
			reject(session, CloseStatus.BAD_DATA.withReason("Ya hay una ejecución en esta conexión"));
			return;
		}
		// Se marca antes de empezar: un segundo start, aunque llegue enseguida,
		// se rechaza.
		session.getAttributes().put(EXECUTION, NOTHING);
		var snippetId = snippetIdOf(session);
		if (snippetId == null) {
			reject(session, new CloseStatus(LiveExecutionRejected.NOT_FOUND, "El snippet no existe"));
			return;
		}
		UUID testId = null;
		if (!start.path("testId").isMissingNode() && !start.path("testId").isNull()) {
			testId = uuidOf(start.path("testId").asString(""));
			if (testId == null) {
				reject(session, new CloseStatus(LiveExecutionRejected.NOT_FOUND, "El test no existe"));
				return;
			}
		}
		try {
			var prepared = executions.prepare(start.path("token").asString(null), snippetId, testId);
			MDC.put(LogContext.USER, prepared.user());
			var sink = (SessionSink) session.getAttributes().get(SINK);
			session.getAttributes().put(EXECUTION, executions.run(prepared, sink));
		} catch (LiveExecutionRejected e) {
			reject(session, e.status());
		} finally {
			MDC.remove(LogContext.USER);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		var execution = (LiveExecution) session.getAttributes().get(EXECUTION);
		if (execution != null) {
			execution.stop();
		}
	}

	// El id sale de la ruta: /snippets/{id}/execute.
	private static UUID snippetIdOf(WebSocketSession session) {
		var uri = session.getUri();
		if (uri == null) {
			return null;
		}
		var parts = uri.getPath().split("/");
		return parts.length < 2 ? null : uuidOf(parts[parts.length - 2]);
	}

	private static UUID uuidOf(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void reject(WebSocketSession session, CloseStatus status) throws IOException {
		log.warn("ejecución en vivo rechazada ({}): {}", status.getCode(), status.getReason());
		session.close(status);
	}

	// Lugar ocupado en la sesión mientras se prepara la ejecución.
	private static final LiveExecution NOTHING = new LiveExecution() {

		@Override
		public void input(String value) {
			// Todavía no empezó: el input se pierde, como si llegara antes de tiempo.
		}

		@Override
		public void stop() {
			// No hay nada que cortar.
		}
	};

	// Manda los mensajes a la UI. Los llama el hilo del cliente de PrintScript.
	private static final class SessionSink implements LiveSink {

		private final WebSocketSession session;
		private final JsonMapper json;

		private SessionSink(WebSocketSession session, JsonMapper json) {
			this.session = session;
			this.json = json;
		}

		@Override
		public void send(LiveMessage message) {
			if (!session.isOpen()) {
				return;
			}
			try {
				session.sendMessage(new TextMessage(json.writeValueAsString(message)));
			} catch (IOException e) {
				log.warn("No se pudo mandar un mensaje de la ejecución en vivo a la UI", e);
			}
		}

		@Override
		public void close(CloseStatus status) {
			try {
				if (session.isOpen()) {
					session.close(status);
				}
			} catch (IOException e) {
				log.warn("No se pudo cerrar la conexión de la ejecución en vivo", e);
			}
		}
	}
}
