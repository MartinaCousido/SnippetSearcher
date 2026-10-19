package com.snippetsearcher.snippets.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;

// POST /snippets contra la base real. Los otros servicios (PrintScript, Asset
// Service y Permission Service) se simulan: sus clientes tienen tests propios.
@SpringBootTest
@AutoConfigureMockMvc
class CreateSnippetTests {

	private static final String CODE = "let saludo: string = \"Hola\";\nprintln(saludo);";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SnippetRepository repository;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of());
	}

	private ResultActions create(String body) throws Exception {
		return mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String body(String language, String version) {
		var versionField = version == null ? "" : ", \"version\": \"" + version + "\"";
		return """
				{"name": "saludo", "content": "let saludo: string = \\"Hola\\";\\nprintln(saludo);",
				 "language": "%s", "extension": "ps"%s}
				""".formatted(language, versionField);
	}

	@Test
	void aValidSnippetIsSavedWithItsOwner() throws Exception {
		create(body("printscript", "1.0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("saludo"))
				.andExpect(jsonPath("$.description").value(""))
				.andExpect(jsonPath("$.language").value("printscript"))
				.andExpect(jsonPath("$.version").value("1.0"))
				.andExpect(jsonPath("$.extension").value("ps"))
				.andExpect(jsonPath("$.content").value(CODE))
				.andExpect(jsonPath("$.compliance").value("pending"))
				.andExpect(jsonPath("$.author").value("dev-user"));

		var snippet = repository.findAll().getFirst();
		verify(printScript).validate(CODE, "1.0");
		verify(storage).put(snippet.contentKey(), CODE);
		verify(permissions).registerOwner(snippet.id());
		assertThat(snippet.compliance()).isEqualTo(Compliance.PENDING);
	}

	@Test
	void withoutAVersionTheLanguageDefaultIsUsed() throws Exception {
		create(body("printscript", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value("1.1"));

		verify(printScript).validate(CODE, "1.1");
	}

	@Test
	void anInvalidSnippetIsRejectedWithTheRuleLineAndColumn() throws Exception {
		var missingSemicolon = new Diagnostic("MISSING_SEMICOLON", "Expected ';'",
				new Range(new Position(2, 1), new Position(2, 7)));
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of(missingSemicolon));

		create(body("printscript", "1.1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.diagnostics[0].rule").value("MISSING_SEMICOLON"))
				.andExpect(jsonPath("$.diagnostics[0].range.start.line").value(2))
				.andExpect(jsonPath("$.diagnostics[0].range.start.column").value(1))
				.andExpect(jsonPath("$.diagnostics[0].range.end.column").value(7));

		assertNothingWasSaved();
	}

	@Test
	void anUnknownLanguageIsABadRequest() throws Exception {
		create(body("cobol", "1.0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Lenguaje no soportado: cobol"));

		assertNothingWasSaved();
	}

	@Test
	void anUnknownVersionIsABadRequest() throws Exception {
		create(body("printscript", "9.9"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Versión de printscript no soportada: 9.9"));

		assertNothingWasSaved();
	}

	@Test
	void missingFieldsAreABadRequest() throws Exception {
		create("{\"content\": \"println(1);\"}").andExpect(status().isBadRequest());

		assertNothingWasSaved();
	}

	@Test
	void ifTheOwnerCannotBeRegisteredNothingIsLeftBehind() throws Exception {
		doThrow(new ResourceAccessException("Permission Service no responde"))
				.when(permissions).registerOwner(any(UUID.class));

		create(body("printscript", "1.1")).andExpect(status().isBadGateway());

		assertThat(repository.count()).isZero();
		verify(storage).put(anyString(), eq(CODE));
		verify(storage).delete(anyString());
	}

	@Test
	void ifPrintScriptDoesNotAnswerNothingIsSaved() throws Exception {
		when(printScript.validate(anyString(), anyString()))
				.thenThrow(new ResourceAccessException("PrintScript no responde"));

		create(body("printscript", "1.1")).andExpect(status().isBadGateway());

		assertNothingWasSaved();
	}

	private void assertNothingWasSaved() {
		assertThat(repository.count()).isZero();
		verify(storage, never()).put(anyString(), anyString());
		verify(permissions, never()).registerOwner(any(UUID.class));
	}
}
