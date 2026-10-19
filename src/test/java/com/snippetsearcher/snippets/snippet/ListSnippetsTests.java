package com.snippetsearcher.snippets.snippet;

import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;

// GET /snippets contra la base real. Permission Service y el Snippet Storage
// se simulan.
@SpringBootTest
@AutoConfigureMockMvc
class ListSnippetsTests {

	private static final UUID SALUDO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID BUCLE = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID AJENO = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID COMPARTIDO = UUID.fromString("44444444-4444-4444-4444-444444444444");

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
		save(SALUDO, "Saludo", "dev-user");
		save(BUCLE, "bucle", "dev-user");
		save(AJENO, "ajeno", "auth0|otro");
		save(COMPARTIDO, "compartido", "auth0|otro");
		when(storage.get(anyString())).thenAnswer(call -> Optional.of("código de " + call.getArgument(0)));
		when(permissions.accessibleSnippets("all")).thenReturn(List.of(SALUDO, BUCLE, COMPARTIDO));
		when(permissions.accessibleSnippets("owner")).thenReturn(List.of(SALUDO, BUCLE));
		when(permissions.accessibleSnippets("shared")).thenReturn(List.of(COMPARTIDO));
	}

	private void save(UUID id, String name, String author) {
		repository.save(new Snippet(id, name, "", "printscript", "1.1", id.toString(), author));
	}

	@Test
	void listsOwnAndSharedSnippetsSortedByName() throws Exception {
		mvc.perform(get("/snippets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.page_size").value(10))
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.snippets[*].name").value(org.hamcrest.Matchers.contains("bucle", "compartido", "Saludo")))
				.andExpect(jsonPath("$.snippets[1].author").value("auth0|otro"))
				.andExpect(jsonPath("$.snippets[2].content").value("código de " + SALUDO))
				.andExpect(jsonPath("$.snippets[2].extension").value("ps"))
				.andExpect(jsonPath("$.snippets[2].compliance").value("pending"));
	}

	@Test
	void ownerListsOnlyTheUsersSnippets() throws Exception {
		mvc.perform(get("/snippets").param("relation", "owner"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(2))
				.andExpect(jsonPath("$.snippets[*].name").value(org.hamcrest.Matchers.contains("bucle", "Saludo")));
	}

	@Test
	void sharedListsOnlyTheSnippetsSharedWithTheUser() throws Exception {
		mvc.perform(get("/snippets").param("relation", "shared"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[*].name").value(org.hamcrest.Matchers.contains("compartido")));
	}

	@Test
	void filtersByNameWithoutCaringAboutCase() throws Exception {
		mvc.perform(get("/snippets").param("name", "SAL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.snippets[0].name").value("Saludo"));
	}

	@Test
	void paginatesStartingAtZero() throws Exception {
		mvc.perform(get("/snippets").param("page", "1").param("page_size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.page_size").value(2))
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.snippets[*].name").value(org.hamcrest.Matchers.contains("Saludo")));
	}

	@Test
	void aUserWithoutSnippetsGetsAnEmptyPage() throws Exception {
		when(permissions.accessibleSnippets("all")).thenReturn(List.of());

		mvc.perform(get("/snippets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0))
				.andExpect(jsonPath("$.snippets", empty()));
	}

	@Test
	void invalidParametersAreABadRequest() throws Exception {
		mvc.perform(get("/snippets").param("page", "-1")).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("page_size", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("relation", "amigos")).andExpect(status().isBadRequest());
		verify(permissions, never()).accessibleSnippets("amigos");
	}
}
