package com.snippetsearcher.snippets.execution;

import org.springframework.web.socket.CloseStatus;

// Hacia dónde van los mensajes de una ejecución en vivo: la conexión con la UI.
public interface LiveSink {

	void send(LiveMessage message);

	void close(CloseStatus status);
}
