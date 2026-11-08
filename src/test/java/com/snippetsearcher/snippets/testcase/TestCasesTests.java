package com.snippetsearcher.snippets.testcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.Language.Evaluation;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.users.UserDirectory;

// Tests de un snippet (US-8), contra la base real. Los otros servicios se
// simulan.
@SpringBootTest
@AutoConfigureMockMvc
class TestCasesTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTRO = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID UNKNOWN = UUID.fromString("99999999-9999-9999-9999-999999999999");
	private static final String CODE = "const n: string = readInput(\"Nombre:\");\nprintln(\"Hola \" + n);";
	private static final String MAYOR = """
			{"name": "mayor", "input": ["Ana"], "output": ["Hola Ana"]}
			""";

	@Autowired
	private MockMvc mvc;

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

	@BeforeEach
	void setUp() {
		snippets.deleteAll();
		snippets.save(new Snippet(SALUDO, "Saludo", "", "printscript", "1.1", SALUDO.toString(), TestUsers.ANA));
		snippets.save(new Snippet(OTRO, "Otro", "", "printscript", "1.1", OTRO.toString(), TestUsers.ANA));
		owner();
		when(permissions.access(OTRO)).thenReturn(new Access(true, true));
		when(storage.get(SALUDO.toString())).thenReturn(Optional.of(CODE));
		// Imprime "Hola " más el primer input.
		when(printScript.evaluate(eq(CODE), eq("1.1"), anyList())).thenAnswer(call -> {
			List<String> inputs = call.getArgument(2);
			return new Evaluation(List.of("Hola " + inputs.getFirst()), List.of());
		});
	}

	private void owner() {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));
	}

	private void sharedWithMe() {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, false));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.with(TestUsers.ana()));
	}

	private ResultActions save(String body) throws Exception {
		return perform(post("/snippets/{id}/tests", SALUDO).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions run(String body) throws Exception {
		return perform(post("/snippets/{id}/tests/run", SALUDO).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String createdId() throws Exception {
		var body = save(MAYOR).andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void theOwnerCreatesATestAndSeesItWithoutAResultYet() throws Exception {
		save(MAYOR)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.name").value("mayor"))
				.andExpect(jsonPath("$.input", contains("Ana")))
				.andExpect(jsonPath("$.output", contains("Hola Ana")))
				.andExpect(jsonPath("$.lastResult", nullValue()));

		perform(get("/snippets/{id}/tests", SALUDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", contains("mayor")));
	}

	@Test
	void testsAreListedInTheOrderTheyWereCreatedAndOnlyThoseOfTheSnippet() throws Exception {
		save("{\"name\": \"primero\"}");
		save("{\"name\": \"segundo\"}");
		perform(post("/snippets/{id}/tests", OTRO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"de otro\"}"));

		perform(get("/snippets/{id}/tests", SALUDO))
				.andExpect(jsonPath("$[*].name", contains("primero", "segundo")))
				.andExpect(jsonPath("$[0].input").isEmpty());
	}

	// La UI manda el id al guardar un test que ya existe.
	@Test
	void withItsIdATestIsChangedAndLosesItsLastResult() throws Exception {
		var id = createdId();
		var test = tests.findById(UUID.fromString(id)).orElseThrow();
		test.recordResult(TestResult.SUCCESS);
		tests.save(test);

		save("""
				{"id": "%s", "name": "cambiado", "input": ["Leo"], "output": ["Hola Leo"]}
				""".formatted(id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("cambiado"))
				.andExpect(jsonPath("$.lastResult", nullValue()));
		assertThat(tests.count()).isOne();
	}

	@Test
	void aTestOfAnotherSnippetCannotBeChangedOrDeleted() throws Exception {
		var id = createdId();

		perform(post("/snippets/{id}/tests", OTRO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\": \"%s\", \"name\": \"robado\"}".formatted(id)))
				.andExpect(status().isNotFound());
		perform(delete("/snippets/{id}/tests/{testId}", OTRO, id)).andExpect(status().isNotFound());
		assertThat(tests.findById(UUID.fromString(id))).get().extracting(TestCase::name).isEqualTo("mayor");
	}

	@Test
	void aTestNeedsAName() throws Exception {
		save("{\"input\": [\"Ana\"]}").andExpect(status().isBadRequest());
		assertThat(tests.count()).isZero();
	}

	@Test
	void theOwnerDeletesATest() throws Exception {
		var id = createdId();

		perform(delete("/snippets/{id}/tests/{testId}", SALUDO, id)).andExpect(status().isNoContent());
		assertThat(tests.count()).isZero();
		perform(delete("/snippets/{id}/tests/{testId}", SALUDO, id)).andExpect(status().isNotFound());
	}

	@Test
	void deletingTheSnippetDeletesItsTests() throws Exception {
		createdId();

		snippets.deleteById(SALUDO);

		assertThat(tests.count()).isZero();
	}

	@Test
	void aTestPassesWhenTheOutputsAreTheSameAndInOrder() throws Exception {
		run("{\"input\": [\"Ana\"], \"output\": [\"Hola Ana\"]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("success"));
		run("{\"input\": [\"Ana\"], \"output\": [\"Hola Leo\"]}")
				.andExpect(jsonPath("$.result").value("fail"));
		run("{\"input\": [\"Ana\"], \"output\": [\"Hola Ana\", \"chau\"]}")
				.andExpect(jsonPath("$.result").value("fail"));
	}

	@Test
	void anErrorWhileRunningIsAFail() throws Exception {
		var error = new Diagnostic("INPUT_UNAVAILABLE", "No hay más inputs",
				new Range(new Position(1, 19), new Position(1, 39)));
		when(printScript.evaluate(eq(CODE), eq("1.1"), eq(List.of())))
				.thenReturn(new Evaluation(List.of(), List.of(error)));

		run("{\"output\": []}").andExpect(jsonPath("$.result").value("fail"));
	}

	// Correr un test guardado, tal como está guardado, guarda su resultado.
	@Test
	void runningASavedTestSavesItsResult() throws Exception {
		var id = createdId();

		run("{\"id\": \"%s\", \"input\": [\"Ana\"], \"output\": [\"Hola Ana\"]}".formatted(id))
				.andExpect(jsonPath("$.result").value("success"));

		perform(get("/snippets/{id}/tests", SALUDO)).andExpect(jsonPath("$[0].lastResult").value("success"));
	}

	// Si se corre con cambios sin guardar, el resultado no es el del test
	// guardado: no se guarda.
	@Test
	void runningASavedTestWithUnsavedChangesDoesNotSaveTheResult() throws Exception {
		var id = createdId();

		run("{\"id\": \"%s\", \"input\": [\"Ana\"], \"output\": [\"Hola Leo\"]}".formatted(id))
				.andExpect(jsonPath("$.result").value("fail"));

		perform(get("/snippets/{id}/tests", SALUDO)).andExpect(jsonPath("$[0].lastResult", nullValue()));
	}

	@Test
	void runningATestOfAnotherSnippetIsNotFound() throws Exception {
		var id = createdId();

		perform(post("/snippets/{id}/tests/run", OTRO).contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": \"%s\"}".formatted(id)))
				.andExpect(status().isNotFound());
	}

	// US-7: el usuario con acceso compartido ve y corre los tests, pero no los
	// crea, cambia ni borra.
	@Test
	void aSharedUserSeesAndRunsTheTestsButCannotChangeThem() throws Exception {
		var id = createdId();
		sharedWithMe();

		perform(get("/snippets/{id}/tests", SALUDO)).andExpect(status().isOk());
		run("{\"input\": [\"Ana\"], \"output\": [\"Hola Ana\"]}").andExpect(jsonPath("$.result").value("success"));
		save(MAYOR).andExpect(status().isForbidden());
		perform(delete("/snippets/{id}/tests/{testId}", SALUDO, id)).andExpect(status().isForbidden());
		assertThat(tests.count()).isOne();
	}

	@Test
	void withoutAccessItIsForbiddenAndAMissingSnippetIsNotFound() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		perform(get("/snippets/{id}/tests", SALUDO)).andExpect(status().isForbidden());
		run("{}").andExpect(status().isForbidden());
		perform(get("/snippets/{id}/tests", UNKNOWN)).andExpect(status().isNotFound());
		perform(post("/snippets/{id}/tests/run", UNKNOWN).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
	}

	// Correr un test pide read:snippets; crearlo, write:snippets.
	@Test
	void runningOnlyNeedsReadSnippets() throws Exception {
		var lector = TestUsers.user(TestUsers.ANA, "read:snippets");

		mvc.perform(post("/snippets/{id}/tests/run", SALUDO).contentType(MediaType.APPLICATION_JSON)
						.content("{\"input\": [\"Ana\"], \"output\": [\"Hola Ana\"]}").with(lector))
				.andExpect(status().isOk());
		mvc.perform(post("/snippets/{id}/tests", SALUDO).contentType(MediaType.APPLICATION_JSON).content(MAYOR)
						.with(lector))
				.andExpect(status().isForbidden());
		mvc.perform(get("/snippets/{id}/tests", SALUDO)).andExpect(status().isUnauthorized());
	}

	@Test
	void savingATestDoesNotRunIt() throws Exception {
		save(MAYOR);
		verifyNoInteractions(printScript);
	}
}
