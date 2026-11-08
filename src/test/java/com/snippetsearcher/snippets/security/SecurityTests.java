package com.snippetsearcher.snippets.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.snippet.SnippetRepository;
import com.snippetsearcher.snippets.users.UserDirectory;

// Quién puede llamar a cada endpoint (SecurityConfig): sin token, 401; sin el
// permiso que pide la operación, 403. Los permisos de cada snippet (owner o
// compartido) se prueban en los tests de cada operación.
@SpringBootTest(properties = "cors.allowed-origins=https://snippetsearcher-dev.duckdns.org")
@AutoConfigureMockMvc
class SecurityTests {

	private static final String SNIPPET = """
			{"name": "saludo", "content": "println(1);", "language": "printscript", "extension": "ps"}
			""";

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

	// Auth0 (módulo User): sin email, el autor se muestra con su id.
	@MockitoBean
	private UserDirectory users;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		when(printScript.validate(anyString(), anyString())).thenReturn(List.of());
	}

	@Test
	void withoutATokenItIsUnauthorized() throws Exception {
		mvc.perform(get("/snippets")).andExpect(status().isUnauthorized());
		mvc.perform(get("/languages")).andExpect(status().isUnauthorized());
		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(SNIPPET))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theHealthDoesNotNeedAToken() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	// El usuario sin rol: tiene sesión, pero su token no trae permisos.
	@Test
	void withoutPermissionsItIsForbidden() throws Exception {
		var sinRol = TestUsers.user("auth0|sin-rol", "");

		mvc.perform(get("/languages").with(sinRol)).andExpect(status().isForbidden());
		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(SNIPPET).with(sinRol))
				.andExpect(status().isForbidden());
	}

	@Test
	void readingOnlyNeedsReadSnippets() throws Exception {
		var lector = TestUsers.user("auth0|lector", "read:snippets");

		mvc.perform(get("/languages").with(lector)).andExpect(status().isOk());
		// Correr un test y formatear no guardan nada: alcanza con leer. Pasan la
		// seguridad; la respuesta es la del endpoint: el snippet no existe, o el
		// endpoint todavía no existe (formatear).
		mvc.perform(post("/snippets/{id}/tests/run", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
						.content("{}").with(lector))
				.andExpect(status().isNotFound());
		mvc.perform(post("/snippets/{id}/format", UUID.randomUUID()).with(lector))
				.andExpect(status().isNotFound());
	}

	@Test
	void writingNeedsWriteSnippets() throws Exception {
		var lector = TestUsers.user("auth0|lector", "read:snippets");

		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(SNIPPET).with(lector))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/snippets/{id}", UUID.randomUUID()).with(lector))
				.andExpect(status().isForbidden());
	}

	// El author es el "sub" del token de quien crea el snippet.
	@Test
	void theAuthorIsTheUserOfTheToken() throws Exception {
		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(SNIPPET).with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.author").value(TestUsers.ANA));
		mvc.perform(post("/snippets").contentType(MediaType.APPLICATION_JSON).content(SNIPPET)
						.with(TestUsers.user("auth0|leo", "read:snippets write:snippets")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.author").value("auth0|leo"));
	}

	// El navegador manda el preflight sin token: tiene que responderse igual.
	@Test
	void thePreflightDoesNotNeedAToken() throws Exception {
		mvc.perform(options("/snippets")
						.header("Origin", "https://snippetsearcher-dev.duckdns.org")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://snippetsearcher-dev.duckdns.org"));
	}
}
