package com.snippetsearcher.snippets.clients;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.snippetsearcher.snippets.clients.Language.LiveExecution;
import com.snippetsearcher.snippets.clients.Language.LiveListener;
import com.snippetsearcher.snippets.observability.LogContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Una ejecución en vivo en el servicio de PrintScript, por su WebSocket
// /execute (ver "Ejecución en vivo" en Decisiones técnicas). Traduce los
// mensajes de PrintScript a llamadas de LiveListener.
// La conexión vive lo que dura la ejecución: la cierra stop() o PrintScript al
// terminar, no el método que la usa (por eso CloseResource no aplica).
@SuppressWarnings("PMD.CloseResource")
final class PrintScriptLiveExecution extends TextWebSocketHandler implements LiveExecution {

	private static final Logger log = LoggerFactory.getLogger(PrintScriptLiveExecution.class);
	private static final long CONNECT_TIMEOUT_SECONDS = 5;
	private static final int SEND_TIME_LIMIT_MS = 10_000;
	// El código viaja en el primer mensaje: hasta 5 MB, como acepta el proxy.
	private static final int MAX_MESSAGE_BYTES = 5 * 1024 * 1024;
	private static final String TYPE = "type";

	private final JsonMapper json;
	private final LiveListener listener;
	private volatile WebSocketSession session;
	private volatile boolean ended;

	private PrintScriptLiveExecution(JsonMapper json, LiveListener listener) {
		this.json = json;
		this.listener = listener;
	}

	// Abre la conexión y manda el start. Si PrintScript no responde, falla con
	// IllegalStateException.
	static PrintScriptLiveExecution start(WebSocketClient client, URI uri, JsonMapper json, String code, String version,
			List<String> inputs, LiveListener listener) {
		var execution = new PrintScriptLiveExecution(json, listener);
		var headers = new WebSocketHttpHeaders();
		var requestId = MDC.get(LogContext.REQUEST_ID);
		if (requestId != null) {
			headers.add("X-Request-Id", requestId);
		}
		WebSocketSession opened;
		try {
			opened = client.execute(execution, headers, uri).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("No se pudo conectar con el servicio de PrintScript", e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("No se pudo conectar con el servicio de PrintScript", e);
		}
		opened.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
		execution.session = new ConcurrentWebSocketSessionDecorator(opened, SEND_TIME_LIMIT_MS, MAX_MESSAGE_BYTES);
		var start = new LinkedHashMap<String, Object>();
		start.put(TYPE, "start");
		start.put("code", code);
		start.put("version", version);
		if (inputs != null) {
			start.put("inputs", inputs);
		}
		execution.send(start);
		return execution;
	}

	@Override
	public void input(String value) {
		var input = new LinkedHashMap<String, Object>();
		input.put(TYPE, "input");
		input.put("value", value);
		send(input);
	}

	@Override
	public void stop() {
		var current = session;
		if (current != null && current.isOpen()) {
			try {
				current.close(CloseStatus.NORMAL);
			} catch (IOException e) {
				log.warn("No se pudo cerrar la ejecución en vivo en PrintScript", e);
			}
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession from, TextMessage message) {
		JsonNode received;
		try {
			received = json.readTree(message.getPayload());
		} catch (JacksonException e) {
			log.warn("PrintScript mandó un mensaje que no es JSON");
			return;
		}
		switch (received.path(TYPE).asString("")) {
			case "output" -> listener.output(received.path("value").asString(""));
			case "input-request" -> listener.inputRequest(received.path("prompt").asString(""));
			case "error" -> listener.error(json.treeToValue(received.path("diagnostic"), Diagnostic.class));
			case "end" -> {
				ended = true;
				listener.end();
			}
			default -> log.warn("PrintScript mandó un mensaje desconocido: {}", received.path(TYPE).asString(""));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession from, CloseStatus status) {
		if (!ended) {
			listener.failure("PrintScript cerró la ejecución sin terminarla (" + status.getCode() + ")");
		}
	}

	private void send(Object message) {
		var current = session;
		if (current == null || !current.isOpen()) {
			return;
		}
		try {
			current.sendMessage(new TextMessage(json.writeValueAsString(message)));
		} catch (IOException e) {
			log.warn("No se pudo mandar un mensaje a la ejecución en vivo de PrintScript", e);
			stop();
		}
	}
}
