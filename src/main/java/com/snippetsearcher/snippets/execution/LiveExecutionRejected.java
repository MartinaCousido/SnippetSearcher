package com.snippetsearcher.snippets.execution;

import org.springframework.web.socket.CloseStatus;

// La ejecución en vivo no puede empezar: la conexión se cierra con este código
// y motivo (ver "Ejecución en vivo" en Decisiones técnicas).
public class LiveExecutionRejected extends RuntimeException {

	private static final long serialVersionUID = 1L;

	static final int UNAUTHORIZED = 4401;
	static final int FORBIDDEN = 4403;
	static final int NOT_FOUND = 4404;

	private final transient CloseStatus status;

	public LiveExecutionRejected(int code, String reason) {
		super(reason);
		this.status = new CloseStatus(code, reason);
	}

	public LiveExecutionRejected(int code, String reason, Throwable cause) {
		super(reason, cause);
		this.status = new CloseStatus(code, reason);
	}

	public CloseStatus status() {
		return status;
	}
}
