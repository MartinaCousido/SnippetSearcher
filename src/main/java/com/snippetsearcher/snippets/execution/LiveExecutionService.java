package com.snippetsearcher.snippets.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;

import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Language.LiveExecution;
import com.snippetsearcher.snippets.clients.Language.LiveListener;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.language.LanguageCatalog.ResolvedLanguage;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.testcase.TestCase;
import com.snippetsearcher.snippets.testcase.TestCaseRepository;
import com.snippetsearcher.snippets.testcase.TestCaseService;
import com.snippetsearcher.snippets.testcase.TestResult;

// Ejecución en vivo de un snippet (US-9, US-10). Ver "Ejecución en vivo" en
// Decisiones técnicas.
//   - prepare: revisa el token y el permiso, y busca el snippet, su código y
//     el test, si es un test. Si algo falla, LiveExecutionRejected dice con qué
//     código se cierra la conexión.
//   - run: arranca la ejecución en el lenguaje y le pasa a la UI cada cosa que
//     pasa. En un test, compara los outputs con los esperados y guarda el
//     resultado.
@Service
public class LiveExecutionService {

	private static final Logger log = LoggerFactory.getLogger(LiveExecutionService.class);
	private static final String READ = "SCOPE_read:snippets";

	private final JwtDecoder jwtDecoder;
	private final JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
	private final SnippetRepository snippets;
	private final TestCaseRepository tests;
	private final TestCaseService testCases;
	private final PermissionClient permissions;
	private final SnippetStorage storage;
	private final LanguageCatalog languages;

	public LiveExecutionService(JwtDecoder jwtDecoder, SnippetRepository snippets, TestCaseRepository tests,
			TestCaseService testCases, PermissionClient permissions, SnippetStorage storage,
			LanguageCatalog languages) {
		this.jwtDecoder = jwtDecoder;
		this.snippets = snippets;
		this.tests = tests;
		this.testCases = testCases;
		this.permissions = permissions;
		this.storage = storage;
		this.languages = languages;
	}

	// Lo que hace falta para ejecutar. test es null en una ejecución interactiva.
	public record Prepared(UUID snippetId, String user, ResolvedLanguage language, String code, TestCase test) {
	}

	// El token viaja en el primer mensaje, no en el handshake: se valida igual
	// que en los demás pedidos (firma, iss, aud, exp) y pide read:snippets.
	// Mientras se pregunta el permiso, el usuario queda como el del pedido, para
	// que PermissionClient le reenvíe el token a Permission Service.
	public Prepared prepare(String token, UUID snippetId, UUID testId) {
		var user = authenticate(token);
		SecurityContextHolder.getContext().setAuthentication(user);
		try {
			return load(user.getName(), snippetId, testId);
		} catch (LiveExecutionRejected e) {
			throw e;
		} catch (RuntimeException e) {
			log.warn("ejecución en vivo del snippet {}: no se pudo preparar", snippetId, e);
			throw new LiveExecutionRejected(CloseStatus.SERVER_ERROR.getCode(), "No se pudo preparar la ejecución", e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private AbstractAuthenticationToken authenticate(String token) {
		if (token == null || token.isBlank()) {
			throw new LiveExecutionRejected(LiveExecutionRejected.UNAUTHORIZED, "Falta el token");
		}
		Jwt jwt;
		try {
			jwt = jwtDecoder.decode(token);
		} catch (JwtException e) {
			throw new LiveExecutionRejected(LiveExecutionRejected.UNAUTHORIZED, "El token no es válido o venció", e);
		}
		var user = authentication.convert(jwt);
		if (user.getAuthorities().stream().noneMatch(authority -> READ.equals(authority.getAuthority()))) {
			throw new LiveExecutionRejected(LiveExecutionRejected.FORBIDDEN, "Falta el permiso read:snippets");
		}
		return user;
	}

	// Primero se busca el snippet: si no existe es 4404, no 4403.
	private Prepared load(String user, UUID snippetId, UUID testId) {
		var snippet = snippets.findById(snippetId).orElseThrow(
				() -> new LiveExecutionRejected(LiveExecutionRejected.NOT_FOUND, "El snippet no existe"));
		var access = permissions.access(snippetId);
		if (access == null || !access.canRead()) {
			throw new LiveExecutionRejected(LiveExecutionRejected.FORBIDDEN, "No tenés acceso a este snippet");
		}
		var test = testId == null ? null : tests.findByIdAndSnippetId(testId, snippetId).orElseThrow(
				() -> new LiveExecutionRejected(LiveExecutionRejected.NOT_FOUND, "El test no existe"));
		var language = languages.resolve(snippet.language(), snippet.version());
		var code = storage.get(snippet.contentKey()).orElse("");
		return new Prepared(snippetId, user, language, code, test);
	}

	// Arranca la ejecución. Si el lenguaje no responde, LiveExecutionRejected
	// con 1011.
	public LiveExecution run(Prepared prepared, LiveSink sink) {
		var test = prepared.test();
		var mode = test == null ? "interactiva" : "del test " + test.id();
		log.info("ejecución en vivo {} del snippet {}: empieza", mode, prepared.snippetId());
		try {
			return prepared.language().language().executeLive(prepared.code(), prepared.language().version(),
					test == null ? null : test.inputs(), new Relay(prepared, sink, mode));
		} catch (RuntimeException e) {
			log.warn("ejecución en vivo {} del snippet {}: no se pudo empezar", mode, prepared.snippetId(), e);
			throw new LiveExecutionRejected(CloseStatus.SERVER_ERROR.getCode(), "No se pudo ejecutar el snippet", e);
		}
	}

	// Le pasa a la UI lo que manda el lenguaje. En un test, además junta los
	// outputs para compararlos al final: pasa si coinciden exactamente y en
	// orden, y no hubo errores (igual que POST /tests/run).
	private final class Relay implements LiveListener {

		private final Prepared prepared;
		private final LiveSink sink;
		private final String mode;
		private final List<String> outputs = Collections.synchronizedList(new ArrayList<>());
		private volatile boolean failed;

		private Relay(Prepared prepared, LiveSink sink, String mode) {
			this.prepared = prepared;
			this.sink = sink;
			this.mode = mode;
		}

		@Override
		public void output(String value) {
			outputs.add(value);
			sink.send(LiveMessage.output(value));
		}

		@Override
		public void inputRequest(String prompt) {
			sink.send(LiveMessage.inputRequest(prompt));
		}

		@Override
		public void error(Diagnostic diagnostic) {
			failed = true;
			sink.send(LiveMessage.error(diagnostic));
		}

		@Override
		public void end() {
			var test = prepared.test();
			TestResult result = null;
			if (test != null) {
				result = !failed && outputs.equals(test.outputs()) ? TestResult.SUCCESS : TestResult.FAIL;
				testCases.recordLiveResult(prepared.snippetId(), test.id(), test.inputs(), test.outputs(), result);
			}
			log.info("ejecución en vivo {} del snippet {}: termina con {} outputs{}", mode, prepared.snippetId(),
					outputs.size(), result == null ? "" : " (" + result.value() + ")");
			sink.send(LiveMessage.end(result));
			sink.close(CloseStatus.NORMAL);
		}

		@Override
		public void failure(String reason) {
			log.warn("ejecución en vivo {} del snippet {}: se cortó: {}", mode, prepared.snippetId(), reason);
			sink.close(CloseStatus.SERVER_ERROR.withReason("No se pudo terminar la ejecución"));
		}
	}
}
