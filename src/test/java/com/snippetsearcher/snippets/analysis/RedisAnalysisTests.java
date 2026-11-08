package com.snippetsearcher.snippets.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.Language.RuleCatalog;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Compliance;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.users.UserDirectory;

// El consumidor con Redis real: el mensaje se procesa en segundo plano, y si
// falla se reintenta hasta 3 veces y el snippet queda en FAILED. Los tiempos
// de reintento se achican para que el test no tarde.
@SpringBootTest(properties = {"analysis.consumer.enabled=true", "analysis.retry-after=200ms",
		"analysis.retry-interval=100"})
class RedisAnalysisTests {


	@Autowired
	private SnippetRepository snippets;

	@Autowired
	private AnalysisRequests requests;

	@Autowired
	private StringRedisTemplate redis;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@MockitoBean
	private UserDirectory users;

	// Un snippet nuevo en cada test, con su propio código: así no se mezcla con
	// los mensajes de otros snippets (por ejemplo, los que se analizan al
	// arrancar el servicio).
	private UUID snippet;
	private String code;

	@BeforeEach
	void setUp() {
		snippet = UUID.randomUUID();
		code = "println(\"" + snippet + "\");";
		snippets.save(new Snippet(snippet, "Saludo", "", "printscript", "1.1", snippet.toString(), TestUsers.ANA));
		when(storage.get(anyString())).thenReturn(Optional.of(code));
		when(printScript.rules()).thenReturn(new RuleCatalog(List.of(), List.of()));
	}

	private Compliance compliance() {
		return snippets.findById(snippet).orElseThrow().compliance();
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		var limit = Instant.now().plus(Duration.ofSeconds(15));
		while (!condition.getAsBoolean()) {
			assertThat(Instant.now()).as("tardó demasiado").isBefore(limit);
			Thread.sleep(100);
		}
	}

	@Test
	void theLintIsDoneInTheBackground() throws Exception {
		when(printScript.lint(anyString(), anyString(), anyMap())).thenReturn(List.of());

		requests.request(List.of(snippet), Set.of(AnalysisTask.LINT));

		await(() -> compliance() == Compliance.COMPLIANT);
	}

	@Test
	void afterThreeFailedAttemptsTheSnippetIsFailedAndTheMessageIsConfirmed() throws Exception {
		when(printScript.lint(eq(code), anyString(), anyMap())).thenThrow(new IllegalStateException("se cayó"));

		requests.request(List.of(snippet), Set.of(AnalysisTask.LINT));

		await(() -> compliance() == Compliance.FAILED);
		await(() -> redis.opsForStream()
				.pending(AnalysisTask.LINT.stream(), AnalysisConsumer.GROUP).getTotalPendingMessages() == 0);
		verify(printScript, times(3)).lint(eq(code), anyString(), anyMap());
	}
}
