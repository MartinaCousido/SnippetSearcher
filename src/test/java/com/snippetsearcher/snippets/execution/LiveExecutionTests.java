package com.snippetsearcher.snippets.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.Language.LiveExecution;
import com.snippetsearcher.snippets.clients.Language.LiveListener;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.testcase.TestCase;
import com.snippetsearcher.snippets.testcase.TestCaseRepository;
import com.snippetsearcher.snippets.testcase.TestResult;
import com.snippetsearcher.snippets.users.UserDirectory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// WebSocket /snippets/{id}/execute (US-9, US-10), contra la base real. El
// lenguaje, los permisos, el storage y la validación del token se simulan.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveExecutionTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID UNKNOWN = UUID.fromString("99999999-9999-9999-9999-999999999999");
	private static final UUID MAYOR = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final String CODE = "const n: string = readInput(\"Nombre:\");\nprintln(\"Hola \" + n);";
	private static final String TOKEN = "token-de-ana";

	private final JsonMapper json = JsonMapper.builder().build();

	@LocalServerPort
	private int port;

	@Autowired
	private SnippetRepository snippets;

	@Autowired
	private TestCaseRepository tests;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@MockitoBean
	private UserDirectory users;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	private Client client;

	@BeforeEach
	void setUp() {
		tests.deleteAll();
		snippets.deleteAll();
		snippets.save(new Snippet(SALUDO, "Saludo", "", "printscript", "1.1", SALUDO.toString(), TestUsers.ANA));
		tests.save(new TestCase(MAYOR, SALUDO, "mayor", List.of("Ana"), List.of("Hola Ana")));
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));
		when(storage.get(SALUDO.toString())).thenReturn(Optional.of(CODE));
		when(jwtDecoder.decode(TOKEN)).thenReturn(jwt("read:snippets write:snippets"));
		// Sin inputs, pide uno y saluda con lo que llegue. Con inputs, saluda al
		// primero y termina.
		when(printScript.executeLive(eq(CODE), eq("1.1"), isNull(), any())).thenAnswer(call -> {
			LiveListener listener = call.getArgument(3);
			listener.inputRequest("Nombre:");
			return new FakeExecution(listener);
		});
		when(printScript.executeLive(eq(CODE), eq("1.1"), eq(List.of("Ana")), any())).thenAnswer(call -> {
			LiveListener listener = call.getArgument(3);
			listener.output("Hola Ana");
			listener.end();
			return new FakeExecution(listener);
		});
	}

	@AfterEach
	void close() throws Exception {
		if (client != null && client.session.isOpen()) {
			client.session.close();
		}
	}

	@Test
	void interactiveExecutionAsksForTheInputAndStreamsTheOutput() throws Exception {
		client = connect(SALUDO);
		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}");

		var request = client.next();
		assertThat(request.get("type").asString()).isEqualTo("input-request");
		assertThat(request.get("prompt").asString()).isEqualTo("Nombre:");

		client.send("{\"type\": \"input\", \"value\": \"Leo\"}");
		var output = client.next();
		assertThat(output.get("type").asString()).isEqualTo("output");
		assertThat(output.get("value").asString()).isEqualTo("Hola Leo");
		var end = client.next();
		assertThat(end.get("type").asString()).isEqualTo("end");
		assertThat(end.has("result")).isFalse();
		assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(CloseStatus.NORMAL);
	}

	@Test
	void aPassingTestStreamsItsOutputsAndSavesTheResult() throws Exception {
		client = connect(SALUDO);
		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\", \"testId\": \"" + MAYOR + "\"}");

		assertThat(client.next().get("value").asString()).isEqualTo("Hola Ana");
		var end = client.next();
		assertThat(end.get("type").asString()).isEqualTo("end");
		assertThat(end.get("result").asString()).isEqualTo("success");
		client.closed.get(5, TimeUnit.SECONDS);
		assertThat(tests.findById(MAYOR).orElseThrow().lastResult()).isEqualTo(TestResult.SUCCESS);
	}

	@Test
	void aTestWithAnErrorFails() throws Exception {
		when(printScript.executeLive(eq(CODE), eq("1.1"), eq(List.of("Ana")), any())).thenAnswer(call -> {
			LiveListener listener = call.getArgument(3);
			listener.output("Hola Ana");
			listener.error(new Diagnostic("MISSING_INPUT", "Falta un input",
					new Range(new Position(1, 1), new Position(1, 5))));
			listener.end();
			return new FakeExecution(listener);
		});
		client = connect(SALUDO);
		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\", \"testId\": \"" + MAYOR + "\"}");

		client.next();
		var error = client.next();
		assertThat(error.get("type").asString()).isEqualTo("error");
		assertThat(error.get("diagnostic").get("rule").asString()).isEqualTo("MISSING_INPUT");
		assertThat(client.next().get("result").asString()).isEqualTo("fail");
		client.closed.get(5, TimeUnit.SECONDS);
		assertThat(tests.findById(MAYOR).orElseThrow().lastResult()).isEqualTo(TestResult.FAIL);
	}

	@Test
	void aTestWithOtherOutputsFails() throws Exception {
		when(printScript.executeLive(eq(CODE), eq("1.1"), eq(List.of("Ana")), any())).thenAnswer(call -> {
			LiveListener listener = call.getArgument(3);
			listener.output("Chau Ana");
			listener.end();
			return new FakeExecution(listener);
		});
		client = connect(SALUDO);
		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\", \"testId\": \"" + MAYOR + "\"}");

		client.next();
		assertThat(client.next().get("result").asString()).isEqualTo("fail");
	}

	@Test
	void withoutATokenTheConnectionClosesWith4401() throws Exception {
		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\"}")).isEqualTo(4401);
	}

	@Test
	void anInvalidTokenClosesWith4401() throws Exception {
		when(jwtDecoder.decode("vencido")).thenThrow(new BadJwtException("vencido"));

		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\", \"token\": \"vencido\"}")).isEqualTo(4401);
	}

	@Test
	void withoutReadPermissionTheConnectionClosesWith4403() throws Exception {
		when(jwtDecoder.decode("sin-permisos")).thenReturn(jwt(""));

		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\", \"token\": \"sin-permisos\"}")).isEqualTo(4403);
	}

	@Test
	void withoutAccessToTheSnippetTheConnectionClosesWith4403() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}")).isEqualTo(4403);
	}

	@Test
	void anUnknownSnippetClosesWith4404() throws Exception {
		assertThat(closeCodeFor(UNKNOWN, "{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}")).isEqualTo(4404);
	}

	@Test
	void anUnknownTestClosesWith4404() throws Exception {
		assertThat(closeCodeFor(SALUDO,
				"{\"type\": \"start\", \"token\": \"" + TOKEN + "\", \"testId\": \"" + UNKNOWN + "\"}"))
				.isEqualTo(4404);
	}

	@Test
	void ifPrintScriptDoesNotAnswerTheConnectionClosesWith1011() throws Exception {
		when(printScript.executeLive(anyString(), anyString(), isNull(), any()))
				.thenThrow(new IllegalStateException("No se pudo conectar con el servicio de PrintScript"));

		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}")).isEqualTo(1011);
	}

	@Test
	void ifTheExecutionIsCutTheConnectionClosesWith1011() throws Exception {
		when(printScript.executeLive(eq(CODE), eq("1.1"), isNull(), any())).thenAnswer(call -> {
			LiveListener listener = call.getArgument(3);
			listener.failure("PrintScript se cayó");
			return new FakeExecution(listener);
		});

		assertThat(closeCodeFor(SALUDO, "{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}")).isEqualTo(1011);
	}

	@Test
	void anInputBeforeStartClosesWith1007() throws Exception {
		assertThat(closeCodeFor(SALUDO, "{\"type\": \"input\", \"value\": \"Ana\"}")).isEqualTo(1007);
	}

	@Test
	void aMessageThatIsNotJsonClosesWith1007() throws Exception {
		assertThat(closeCodeFor(SALUDO, "hola")).isEqualTo(1007);
	}

	@Test
	void anUnknownMessageTypeClosesWith1007() throws Exception {
		assertThat(closeCodeFor(SALUDO, "{\"type\": \"pausa\"}")).isEqualTo(1007);
	}

	@Test
	void aSecondStartClosesWith1007() throws Exception {
		client = connect(SALUDO);
		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}");
		assertThat(client.next().get("type").asString()).isEqualTo("input-request");

		client.send("{\"type\": \"start\", \"token\": \"" + TOKEN + "\"}");

		assertThat(client.closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(1007);
	}

	private int closeCodeFor(UUID snippetId, String firstMessage) throws Exception {
		client = connect(snippetId);
		client.send(firstMessage);
		return client.closed.get(5, TimeUnit.SECONDS).getCode();
	}

	private static Jwt jwt(String scope) {
		return Jwt.withTokenValue("token").header("alg", "none").subject(TestUsers.ANA).claim("scope", scope)
				.build();
	}

	private Client connect(UUID snippetId) throws Exception {
		var created = new Client();
		created.session = new StandardWebSocketClient()
				.execute(created, "ws://localhost:" + port + "/snippets/" + snippetId + "/execute")
				.get(5, TimeUnit.SECONDS);
		return created;
	}

	// Una ejecución simulada: cada input saluda y termina.
	private record FakeExecution(LiveListener listener) implements LiveExecution {

		@Override
		public void input(String value) {
			listener.output("Hola " + value);
			listener.end();
		}

		@Override
		public void stop() {
			// No hay nada que cortar.
		}
	}

	private final class Client extends TextWebSocketHandler {

		private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
		private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
		private WebSocketSession session;

		@Override
		protected void handleTextMessage(WebSocketSession from, TextMessage message) {
			messages.add(message.getPayload());
		}

		@Override
		public void afterConnectionClosed(WebSocketSession from, CloseStatus status) {
			closed.complete(status);
		}

		void send(String message) throws Exception {
			session.sendMessage(new TextMessage(message));
		}

		JsonNode next() throws Exception {
			var message = messages.poll(5, TimeUnit.SECONDS);
			assertThat(message).as("llegó un mensaje").isNotNull();
			return json.readTree(message);
		}
	}
}
