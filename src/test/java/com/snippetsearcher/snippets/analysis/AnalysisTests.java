package com.snippetsearcher.snippets.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.Language.CatalogRule;
import com.snippetsearcher.snippets.clients.Language.CatalogRule.Default;
import com.snippetsearcher.snippets.clients.Language.Formatted;
import com.snippetsearcher.snippets.clients.Language.RuleCatalog;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.rules.UserRuleRepository;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Compliance;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.users.UserDirectory;

// Lint y formateo en segundo plano (US-12, US-15): qué se publica y qué hace
// el trabajo con cada mensaje. La cola se simula; el consumidor con Redis real
// se prueba en RedisAnalysisTests.
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String CODE = "let mi_saludo: string = \"Hola\";";
	private static final Diagnostic VIOLATION = new Diagnostic("identifier_format", "camelCase",
			new Range(new Position(1, 5), new Position(1, 13)));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SnippetRepository snippets;

	@Autowired
	private AnalysisRequests requests;

	@Autowired
	private UserRuleRepository rules;

	@Autowired
	private PlatformTransactionManager transactions;

	@Autowired
	private AnalysisWorker worker;

	@MockitoBean
	private AnalysisQueue queue;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@MockitoBean
	private UserDirectory users;

	@BeforeEach
	void setUp() {
		// Cada test arranca con las reglas por defecto: otro pudo cambiarlas.
		rules.deleteAll();
		snippets.deleteAll();
		snippets.save(new Snippet(SALUDO, "Saludo", "", "printscript", "1.1", SALUDO.toString(), TestUsers.ANA));
		when(storage.get(SALUDO.toString())).thenReturn(Optional.of(CODE));
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of());
		when(printScript.rules()).thenReturn(new RuleCatalog(
				List.of(new CatalogRule("identifier_format", "text", List.of("camel case", "snake case"), null,
						new Default(true, "camel case"))),
				List.of(new CatalogRule("indent-inside-if", "number", null, null, new Default(true, 4)))));
	}

	private Snippet saved() {
		return snippets.findById(SALUDO).orElseThrow();
	}

	private int requested(AnalysisTask task) {
		requests.request(List.of(SALUDO), Set.of(task));
		return task == AnalysisTask.LINT ? saved().lintRevision() : saved().formatRevision();
	}

	@Test
	void creatingASnippetAsksForItsLintAndFormatAndRespondsPending() throws Exception {
		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"nuevo\", \"content\": \"println(1);\", \"language\": \"printscript\"}")
						.with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compliance").value("pending"));

		var created = snippets.findAll().stream().filter(s -> s.name().equals("nuevo")).findFirst().orElseThrow();
		verify(queue).publish(AnalysisTask.LINT, created.id(), 1);
		verify(queue).publish(AnalysisTask.FORMAT, created.id(), 1);
	}

	@Test
	void updatingASnippetAsksAgainWithANewRevision() throws Exception {
		requested(AnalysisTask.LINT);
		mvc.perform(put("/snippets/{id}", SALUDO).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"println(2);\"}").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.compliance").value("pending"));

		verify(queue).publish(AnalysisTask.LINT, SALUDO, 2);
		verify(queue).publish(AnalysisTask.FORMAT, SALUDO, 1);
	}

	// Al cambiar una regla, se vuelven a lintear los snippets del usuario.
	@Test
	void changingALintRuleAsksForTheLintOfTheSnippetsOfTheUser() throws Exception {
		snippets.save(new Snippet(UUID.randomUUID(), "De otro", "", "printscript", "1.1", "otro", "auth0|otro"));

		mvc.perform(put("/rules/lint").contentType(MediaType.APPLICATION_JSON)
						.content("[{\"name\": \"identifier_format\", \"isActive\": true, \"value\": \"snake case\"}]")
						.with(TestUsers.ana()))
				.andExpect(status().isOk());

		verify(queue).publish(AnalysisTask.LINT, SALUDO, 1);
		assertThat(saved().compliance()).isEqualTo(Compliance.PENDING);
	}

	@Test
	void theLintIsSavedWithTheRulesOfTheOwnerAndShownInTheDetail() throws Exception {
		when(printScript.lint(eq(CODE), eq("1.1"), anyMap())).thenReturn(List.of(VIOLATION));

		worker.process(AnalysisTask.LINT, SALUDO, requested(AnalysisTask.LINT));

		verify(printScript).lint(CODE, "1.1", Map.of("identifier_format", "camel case"));
		assertThat(saved().compliance()).isEqualTo(Compliance.NOT_COMPLIANT);
		mvc.perform(get("/snippets/{id}", SALUDO).with(TestUsers.ana()))
				.andExpect(jsonPath("$.compliance").value("not-compliant"))
				.andExpect(jsonPath("$.lintViolations[0].rule").value("identifier_format"))
				.andExpect(jsonPath("$.lintViolations[0].range.start.column").value(5));
	}

	@Test
	void withoutViolationsItIsCompliant() {
		when(printScript.lint(anyString(), anyString(), anyMap())).thenReturn(List.of());

		worker.process(AnalysisTask.LINT, SALUDO, requested(AnalysisTask.LINT));

		assertThat(saved().compliance()).isEqualTo(Compliance.COMPLIANT);
		assertThat(saved().lintViolations()).isEmpty();
	}

	// Un resultado de una revisión vieja no pisa al nuevo, y procesar dos veces
	// el mismo mensaje da lo mismo.
	@Test
	void anOldRevisionIsDiscarded() {
		when(printScript.lint(anyString(), anyString(), anyMap())).thenReturn(List.of());
		int old = requested(AnalysisTask.LINT);
		int current = requested(AnalysisTask.LINT);
		clearInvocations(printScript);

		worker.process(AnalysisTask.LINT, SALUDO, old);
		assertThat(saved().compliance()).isEqualTo(Compliance.PENDING);

		worker.process(AnalysisTask.LINT, SALUDO, current);
		worker.process(AnalysisTask.LINT, SALUDO, current);
		assertThat(saved().compliance()).isEqualTo(Compliance.COMPLIANT);
	}

	@Test
	void theFormattedVersionIsASecondFileAndTheOriginalIsNotTouched() {
		when(printScript.format(eq(CODE), eq("1.1"), anyMap())).thenReturn(new Formatted("formateado", List.of()));

		worker.process(AnalysisTask.FORMAT, SALUDO, requested(AnalysisTask.FORMAT));

		verify(printScript).format(CODE, "1.1", Map.of("indent-inside-if", 4));
		verify(storage).put(SALUDO + ".formatted", "formateado");
		assertThat(saved().formattedKey()).isEqualTo(SALUDO + ".formatted");
	}

	@Test
	void ifTheFormatterFailsItIsRetried() {
		when(printScript.format(anyString(), anyString(), anyMap()))
				.thenReturn(new Formatted(CODE, List.of(VIOLATION)));
		int revision = requested(AnalysisTask.FORMAT);

		assertThatThrownBy(() -> worker.process(AnalysisTask.FORMAT, SALUDO, revision))
				.isInstanceOf(RuntimeException.class);
		assertThat(saved().formattedKey()).isNull();
	}

	// Se agotaron los intentos: el lint queda en FAILED; el formateo, sin versión.
	@Test
	void givingUpLeavesTheLintFailedAndNoFormattedVersion() {
		worker.giveUp(AnalysisTask.LINT, SALUDO, requested(AnalysisTask.LINT));
		worker.giveUp(AnalysisTask.FORMAT, SALUDO, requested(AnalysisTask.FORMAT));

		assertThat(saved().compliance()).isEqualTo(Compliance.FAILED);
		assertThat(saved().formattedKey()).isNull();
	}

	// El lint y el formateo corren a la vez: si el formateo termina después,
	// no pisa el resultado del lint con el estado que había leído.
	@Test
	void aFormatThatEndsAfterTheLintDoesNotOverwriteIt() {
		when(printScript.lint(anyString(), anyString(), anyMap())).thenReturn(List.of());
		when(printScript.format(anyString(), anyString(), anyMap())).thenReturn(new Formatted("f", List.of()));
		int lint = requested(AnalysisTask.LINT);
		int format = requested(AnalysisTask.FORMAT);
		var separate = new TransactionTemplate(transactions);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		// Mientras el formateo está en curso (ya leyó el snippet), termina el lint.
		doAnswer(call -> {
			separate.executeWithoutResult(status -> worker.process(AnalysisTask.LINT, SALUDO, lint));
			return null;
		}).when(storage).put(anyString(), anyString());

		worker.process(AnalysisTask.FORMAT, SALUDO, format);

		assertThat(saved().compliance()).isEqualTo(Compliance.COMPLIANT);
		assertThat(saved().formattedKey()).isEqualTo(SALUDO + ".formatted");
	}

	// Los snippets de antes de #23 nunca se analizaron: se piden una sola vez.
	@Test
	void theSnippetsThatWereNeverAnalyzedAreRequestedOnce() {
		requests.requestNeverAnalyzed();
		requests.requestNeverAnalyzed();

		verify(queue).publish(AnalysisTask.LINT, SALUDO, 1);
		verify(queue).publish(AnalysisTask.FORMAT, SALUDO, 1);
		assertThat(saved().compliance()).isEqualTo(Compliance.PENDING);
	}

	@Test
	void aSnippetThatNoLongerExistsIsSkipped() {
		worker.process(AnalysisTask.LINT, UUID.randomUUID(), 1);
	}

	@Test
	void deletingASnippetDeletesItsFormattedVersion() throws Exception {
		when(printScript.format(anyString(), anyString(), anyMap())).thenReturn(new Formatted("f", List.of()));
		worker.process(AnalysisTask.FORMAT, SALUDO, requested(AnalysisTask.FORMAT));

		mvc.perform(delete("/snippets/{id}", SALUDO).with(TestUsers.ana())).andExpect(status().isNoContent());

		verify(storage).delete(SALUDO.toString());
		verify(storage).delete(SALUDO + ".formatted");
	}
}
