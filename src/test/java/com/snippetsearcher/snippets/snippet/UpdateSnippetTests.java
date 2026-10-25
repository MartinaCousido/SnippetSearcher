package com.snippetsearcher.snippets.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;

// PUT /snippets/{id} contra la base real. Los otros servicios se simulan.
@SpringBootTest
@AutoConfigureMockMvc
class UpdateSnippetTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID UNKNOWN = UUID.fromString("99999999-9999-9999-9999-999999999999");
	private static final String NEW_CODE = "println(\"chau\");";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SnippetRepository repository;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		repository.save(new Snippet(SALUDO, "Saludo", "Saluda", "printscript", "1.0", SALUDO.toString(),
				"dev-user"));
		// Ya se habría linteado: así se ve que el update lo vuelve a pending.
		jdbc.update("UPDATE snippets SET compliance = 'compliant'");
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of());
	}

	private ResultActions update(UUID id, String body) throws Exception {
		return mvc.perform(put("/snippets/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Snippet saved() {
		return repository.findById(SALUDO).orElseThrow();
	}

	// Lo que manda la UI de la cátedra: solo el código.
	@Test
	void theOwnerUpdatesTheCode() throws Exception {
		update(SALUDO, "{\"content\": \"println(\\\"chau\\\");\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(SALUDO.toString()))
				.andExpect(jsonPath("$.name").value("Saludo"))
				.andExpect(jsonPath("$.description").value("Saluda"))
				.andExpect(jsonPath("$.version").value("1.0"))
				.andExpect(jsonPath("$.content").value(NEW_CODE))
				.andExpect(jsonPath("$.compliance").value("pending"));

		verify(printScript).validate(NEW_CODE, "1.0");
		verify(storage).put(SALUDO.toString(), NEW_CODE);
		assertThat(saved().compliance()).isEqualTo(Compliance.PENDING);
	}

	@Test
	void theOwnerUpdatesTheMetadata() throws Exception {
		update(SALUDO, """
				{"content": "println(\\"chau\\");", "name": "Despedida", "description": "Se despide"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Despedida"))
				.andExpect(jsonPath("$.description").value("Se despide"));

		assertThat(saved().name()).isEqualTo("Despedida");
		assertThat(saved().description()).isEqualTo("Se despide");
	}

	@Test
	void changingTheVersionValidatesWithTheNewOne() throws Exception {
		update(SALUDO, "{\"content\": \"println(\\\"chau\\\");\", \"version\": \"1.1\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value("1.1"));

		verify(printScript).validate(NEW_CODE, "1.1");
		assertThat(saved().version()).isEqualTo("1.1");
	}

	@Test
	void anInvalidCodeDoesNotReplaceThePreviousOne() throws Exception {
		var missingSemicolon = new Diagnostic("MISSING_SEMICOLON", "Expected ';'",
				new Range(new Position(1, 17), new Position(1, 17)));
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of(missingSemicolon));

		update(SALUDO, "{\"content\": \"println(\\\"chau\\\")\", \"name\": \"Despedida\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.diagnostics[0].rule").value("MISSING_SEMICOLON"))
				.andExpect(jsonPath("$.diagnostics[0].range.start.line").value(1))
				.andExpect(jsonPath("$.diagnostics[0].range.start.column").value(17));

		assertNothingChanged();
	}

	@Test
	void anUnknownVersionIsABadRequest() throws Exception {
		update(SALUDO, "{\"content\": \"println(1);\", \"version\": \"9.9\"}")
				.andExpect(status().isBadRequest());

		assertNothingChanged();
	}

	@Test
	void aUserItWasSharedWithCannotUpdateIt() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, false));

		update(SALUDO, "{\"content\": \"println(1);\"}").andExpect(status().isForbidden());

		verify(printScript, never()).validate(anyString(), anyString());
		assertNothingChanged();
	}

	@Test
	void aUserWithoutAccessCannotUpdateIt() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		update(SALUDO, "{\"content\": \"println(1);\"}").andExpect(status().isForbidden());

		assertNothingChanged();
	}

	// Primero se busca: si no existe es 404, sin preguntarle a Permisos.
	@Test
	void anUnknownSnippetIsNotFound() throws Exception {
		update(UNKNOWN, "{\"content\": \"println(1);\"}").andExpect(status().isNotFound());

		verify(permissions, never()).access(any());
	}

	@Test
	void theCodeIsRequired() throws Exception {
		update(SALUDO, "{\"name\": \"Despedida\"}").andExpect(status().isBadRequest());

		assertNothingChanged();
	}

	@Test
	void theNameCannotBeBlank() throws Exception {
		update(SALUDO, "{\"content\": \"println(1);\", \"name\": \"  \"}").andExpect(status().isBadRequest());

		assertNothingChanged();
	}

	// Si no se puede guardar el archivo, los metadatos tampoco cambian.
	@Test
	void ifTheCodeCannotBeSavedTheMetadataDoesNotChange() throws Exception {
		doThrow(new ResourceAccessException("Asset Service caído")).when(storage)
				.put(eq(SALUDO.toString()), anyString());

		update(SALUDO, "{\"content\": \"println(1);\", \"name\": \"Despedida\"}")
				.andExpect(status().isBadGateway());

		assertThat(saved().name()).isEqualTo("Saludo");
		assertThat(saved().compliance()).isEqualTo(Compliance.COMPLIANT);
	}

	private void assertNothingChanged() {
		verify(storage, never()).put(anyString(), anyString());
		var snippet = saved();
		assertThat(snippet.name()).isEqualTo("Saludo");
		assertThat(snippet.version()).isEqualTo("1.0");
		assertThat(snippet.compliance()).isEqualTo(Compliance.COMPLIANT);
	}
}
