package com.snippetsearcher.snippets.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.users.UserDirectory;

// Reglas de lint y formateo de cada usuario (US-11, US-14) y el formateo de un
// snippet, contra la base real. El catálogo del lenguaje se simula.
@SpringBootTest
@AutoConfigureMockMvc
class RulesTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final RuleCatalog CATALOG = new RuleCatalog(
			List.of(new CatalogRule("identifier_format", "text", List.of("camel case", "snake case"), null,
							new Default(true, "camel case")),
					new CatalogRule("mandatory-variable-or-literal-in-println", "boolean", null, null,
							new Default(true, null))),
			List.of(new CatalogRule("enforce-spacing-around-equals", "boolean", null,
							"enforce-no-spacing-around-equals", new Default(true, null)),
					new CatalogRule("enforce-no-spacing-around-equals", "boolean", null,
							"enforce-spacing-around-equals", new Default(false, null)),
					new CatalogRule("indent-inside-if", "number", null, null, new Default(true, 4))));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRuleRepository rules;

	@Autowired
	private SnippetRepository snippets;

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
		rules.deleteAll();
		snippets.deleteAll();
		when(printScript.rules()).thenReturn(CATALOG);
	}

	private ResultActions putRules(String kind, String body, RequestPostProcessor user) throws Exception {
		return mvc.perform(put("/rules/{kind}", kind).contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user));
	}

	@Test
	void aNewUserHasTheDefaultRulesOfTheLanguage() throws Exception {
		mvc.perform(get("/rules/lint").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("identifier_format"))
				.andExpect(jsonPath("$[0].name").value("identifier_format"))
				.andExpect(jsonPath("$[0].isActive").value(true))
				.andExpect(jsonPath("$[0].value").value("camel case"))
				.andExpect(jsonPath("$[1].value").doesNotExist());
		mvc.perform(get("/rules/format").with(TestUsers.ana()))
				.andExpect(jsonPath("$[2].value").value(4));
	}

	@Test
	void eachUserChangesOnlyTheirRules() throws Exception {
		putRules("lint", """
				[{"id": "identifier_format", "name": "identifier_format", "isActive": true, "value": "snake case"},
				 {"id": "mandatory-variable-or-literal-in-println", "name": "mandatory-variable-or-literal-in-println",
				  "isActive": false, "value": null}]
				""", TestUsers.ana())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].value").value("snake case"))
				.andExpect(jsonPath("$[1].isActive").value(false));

		mvc.perform(get("/rules/lint").with(TestUsers.ana()))
				.andExpect(jsonPath("$[0].value").value("snake case"))
				.andExpect(jsonPath("$[1].isActive").value(false));
		mvc.perform(get("/rules/lint").with(TestUsers.user("auth0|leo", "read:snippets write:snippets")))
				.andExpect(jsonPath("$[0].value").value("camel case"))
				.andExpect(jsonPath("$[1].isActive").value(true));
	}

	// La UI manda el valor de un campo numérico como número o como texto.
	@Test
	void aNumberCanComeAsText() throws Exception {
		putRules("format", """
				[{"name": "indent-inside-if", "isActive": true, "value": "2"}]
				""", TestUsers.ana())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[2].value").value(2));
	}

	@Test
	void twoOppositeRulesCannotBeActive() throws Exception {
		putRules("format", """
				[{"name": "enforce-no-spacing-around-equals", "isActive": true}]
				""", TestUsers.ana())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"Las reglas 'enforce-spacing-around-equals' y 'enforce-no-spacing-around-equals' son opuestas: activá solo una"));

		putRules("format", """
				[{"name": "enforce-spacing-around-equals", "isActive": false},
				 {"name": "enforce-no-spacing-around-equals", "isActive": true}]
				""", TestUsers.ana())
				.andExpect(status().isOk());
	}

	@Test
	void aValueThatDoesNotWorkOrAMissingRuleIsABadRequestAndNothingIsSaved() throws Exception {
		putRules("lint", """
				[{"name": "identifier_format", "isActive": true, "value": "kebab case"}]
				""", TestUsers.ana()).andExpect(status().isBadRequest());
		putRules("format", """
				[{"name": "indent-inside-if", "isActive": true, "value": -1}]
				""", TestUsers.ana()).andExpect(status().isBadRequest());
		putRules("format", """
				[{"name": "indent-inside-if", "isActive": true, "value": "cuatro"}]
				""", TestUsers.ana()).andExpect(status().isBadRequest());
		putRules("lint", """
				[{"name": "no-existe", "isActive": true}]
				""", TestUsers.ana()).andExpect(status().isBadRequest());
		assertThat(rules.count()).isZero();
	}

	@Test
	void changingRulesNeedsWriteSnippets() throws Exception {
		putRules("lint", "[]", TestUsers.user(TestUsers.ANA, "read:snippets")).andExpect(status().isForbidden());
		mvc.perform(get("/rules/lint")).andExpect(status().isUnauthorized());
	}

	// --- Formatear un snippet (formatSnippet de la UI) ---

	private ResultActions format(String content) throws Exception {
		return mvc.perform(post("/snippets/{id}/format", SALUDO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\": \"%s\"}".formatted(content)).with(TestUsers.ana()));
	}

	private void snippetOf(String owner, Access access) {
		snippets.save(new Snippet(SALUDO, "Saludo", "", "printscript", "1.1", SALUDO.toString(), owner));
		when(permissions.access(SALUDO)).thenReturn(access);
	}

	@Test
	void formatsWithTheActiveRulesOfTheOwnerWithoutSaving() throws Exception {
		snippetOf("auth0|leo", new Access(true, false));
		putRules("format", """
				[{"name": "indent-inside-if", "isActive": false}]
				""", TestUsers.user("auth0|leo", "read:snippets write:snippets"));
		when(printScript.format(anyString(), anyString(), anyMap())).thenReturn(new Formatted("let a = 1;", List.of()));

		format("let a=1;").andExpect(status().isOk()).andExpect(jsonPath("$.content").value("let a = 1;"));

		// Las reglas de leo (el owner), no las de ana: solo la que dejó activa.
		verify(printScript).format(eq("let a=1;"), eq("1.1"), eq(Map.of("enforce-spacing-around-equals", true)));
	}

	@Test
	void ifTheFormatterFailsItIsABadRequestWithTheDiagnostics() throws Exception {
		snippetOf(TestUsers.ANA, new Access(true, true));
		var error = new Diagnostic("UNKNOWN_SYMBOL", "Símbolo no reconocido: '#'",
				new Range(new Position(1, 17), new Position(1, 17)));
		when(printScript.format(anyString(), anyString(), anyMap())).thenReturn(new Formatted("x", List.of(error)));

		format("let a: number = #;")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.diagnostics[0].rule").value("UNKNOWN_SYMBOL"));
	}

	@Test
	void formattingWithoutAccessIsForbiddenAndAMissingSnippetIsNotFound() throws Exception {
		format("x").andExpect(status().isNotFound());
		snippetOf("auth0|leo", new Access(false, false));
		format("x").andExpect(status().isForbidden());
	}
}
