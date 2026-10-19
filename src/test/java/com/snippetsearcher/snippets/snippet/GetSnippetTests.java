package com.snippetsearcher.snippets.snippet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;

// GET /snippets/{id} contra la base real. Permission Service y el Snippet
// Storage se simulan.
@SpringBootTest
@AutoConfigureMockMvc
class GetSnippetTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID UNKNOWN = UUID.fromString("99999999-9999-9999-9999-999999999999");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SnippetRepository repository;

	@MockitoBean
	private SnippetStorage storage;

	@MockitoBean
	private PermissionClient permissions;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		repository.save(new Snippet(SALUDO, "Saludo", "Saluda", "printscript", "1.1", SALUDO.toString(),
				"auth0|otro"));
		when(storage.get(SALUDO.toString())).thenReturn(Optional.of("println(\"hola\");"));
	}

	@Test
	void theOwnerSeesTheSnippetWithItsCode() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));

		mvc.perform(get("/snippets/{id}", SALUDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(SALUDO.toString()))
				.andExpect(jsonPath("$.name").value("Saludo"))
				.andExpect(jsonPath("$.description").value("Saluda"))
				.andExpect(jsonPath("$.language").value("printscript"))
				.andExpect(jsonPath("$.version").value("1.1"))
				.andExpect(jsonPath("$.extension").value("ps"))
				.andExpect(jsonPath("$.content").value("println(\"hola\");"))
				.andExpect(jsonPath("$.compliance").value("pending"))
				.andExpect(jsonPath("$.author").value("auth0|otro"));
	}

	@Test
	void aUserItWasSharedWithAlsoSeesIt() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, false));

		mvc.perform(get("/snippets/{id}", SALUDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("println(\"hola\");"));
	}

	@Test
	void aUserWithoutAccessGetsForbidden() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		mvc.perform(get("/snippets/{id}", SALUDO)).andExpect(status().isForbidden());
		verify(storage, never()).get(any());
	}

	@Test
	void aSnippetThatDoesNotExistIsNotFound() throws Exception {
		mvc.perform(get("/snippets/{id}", UNKNOWN)).andExpect(status().isNotFound());
		verify(permissions, never()).access(any());
	}

	@Test
	void anIdThatIsNotAUuidIsABadRequest() throws Exception {
		mvc.perform(get("/snippets/{id}", "42")).andExpect(status().isBadRequest());
	}
}
