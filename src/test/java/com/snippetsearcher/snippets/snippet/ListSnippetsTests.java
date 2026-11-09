package com.snippetsearcher.snippets.snippet;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.users.UserDirectory;

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

	// Auth0 (módulo User): sin email, el autor se muestra con su id.
	@MockitoBean
	private UserDirectory users;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		save(SALUDO, "Saludo", TestUsers.ANA);
		save(BUCLE, "bucle", TestUsers.ANA);
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

	// El autor se muestra con su email, que se le pide a Auth0 una sola vez por
	// página. Si no está, con su id.
	@Test
	void showsTheEmailOfTheAuthor() throws Exception {
		when(users.emails(anyCollection())).thenReturn(Map.of("auth0|otro", "otro@example.com"));

		mvc.perform(get("/snippets").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[1].author").value("otro@example.com"))
				.andExpect(jsonPath("$.snippets[2].author").value(TestUsers.ANA));
		verify(users, times(1)).emails(anyCollection());
	}

	@Test
	void listsOwnAndSharedSnippetsSortedByName() throws Exception {
		mvc.perform(get("/snippets").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.page_size").value(10))
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.snippets[*].name").value(contains("bucle", "compartido", "Saludo")))
				.andExpect(jsonPath("$.snippets[1].author").value("auth0|otro"))
				.andExpect(jsonPath("$.snippets[2].content").value("código de " + SALUDO))
				.andExpect(jsonPath("$.snippets[2].extension").value("ps"))
				.andExpect(jsonPath("$.snippets[2].compliance").value("pending"));
	}

	@Test
	void ownerListsOnlyTheUsersSnippets() throws Exception {
		mvc.perform(get("/snippets").param("relation", "owner").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(2))
				.andExpect(jsonPath("$.snippets[*].name").value(contains("bucle", "Saludo")));
	}

	@Test
	void sharedListsOnlyTheSnippetsSharedWithTheUser() throws Exception {
		mvc.perform(get("/snippets").param("relation", "shared").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[*].name").value(contains("compartido")));
	}

	@Test
	void filtersByNameWithoutCaringAboutCase() throws Exception {
		mvc.perform(get("/snippets").param("name", "SAL").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.snippets[0].name").value("Saludo"));
	}

	@Test
	void paginatesStartingAtZero() throws Exception {
		mvc.perform(get("/snippets").param("page", "1").param("page_size", "2").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.page_size").value(2))
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.snippets[*].name").value(contains("Saludo")));
	}

	@Test
	void aUserWithoutSnippetsGetsAnEmptyPage() throws Exception {
		when(permissions.accessibleSnippets("all")).thenReturn(List.of());

		mvc.perform(get("/snippets").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0))
				.andExpect(jsonPath("$.snippets", empty()));
	}

	@Test
	void invalidParametersAreABadRequest() throws Exception {
		mvc.perform(get("/snippets").param("page", "-1").with(TestUsers.ana())).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("page_size", "0").with(TestUsers.ana())).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("relation", "amigos").with(TestUsers.ana())).andExpect(status().isBadRequest());
		verify(permissions, never()).accessibleSnippets("amigos");
	}

	// US-5: filtrar y ordenar por validez. Saludo queda compliant, bucle
	// not-compliant y compartido sigue pending.
	private void lint() {
		var saludo = repository.findById(SALUDO).orElseThrow();
		saludo.recordLint(saludo.requestLint(), List.of());
		repository.save(saludo);
		var bucle = repository.findById(BUCLE).orElseThrow();
		bucle.recordLint(bucle.requestLint(), List.of(new Diagnostic("identifier_format", "camelCase",
				new Range(new Position(1, 5), new Position(1, 9)))));
		repository.save(bucle);
	}

	@Test
	void filtersByCompliance() throws Exception {
		lint();

		mvc.perform(get("/snippets").param("compliance", "not-compliant").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.snippets[*].name").value(contains("bucle")));
	}

	@Test
	void filtersByLanguage() throws Exception {
		mvc.perform(get("/snippets").param("language", "printscript").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(3));
	}

	@Test
	void combinesFiltersWithTheName() throws Exception {
		lint();

		mvc.perform(get("/snippets").param("name", "sal").param("compliance", "pending").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/snippets").param("name", "sal").param("compliance", "compliant").with(TestUsers.ana()))
				.andExpect(jsonPath("$.snippets[*].name").value(contains("Saludo")));
	}

	@Test
	void sortsByComplianceAndThenByName() throws Exception {
		lint();

		mvc.perform(get("/snippets").param("sort", "compliance").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[*].compliance").value(contains("compliant", "not-compliant", "pending")));
		mvc.perform(get("/snippets").param("sort", "compliance").param("order", "desc").with(TestUsers.ana()))
				.andExpect(jsonPath("$.snippets[*].compliance").value(contains("pending", "not-compliant", "compliant")));
	}

	@Test
	void sortsByNameDescending() throws Exception {
		mvc.perform(get("/snippets").param("order", "desc").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[*].name").value(contains("Saludo", "compartido", "bucle")));
	}

	@Test
	void sortsByLanguageAndThenByName() throws Exception {
		mvc.perform(get("/snippets").param("sort", "language").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.snippets[*].name").value(contains("bucle", "compartido", "Saludo")));
	}

	@Test
	void unknownFilterOrSortValuesAreABadRequest() throws Exception {
		mvc.perform(get("/snippets").param("compliance", "valido").with(TestUsers.ana())).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("sort", "author").with(TestUsers.ana())).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("order", "arriba").with(TestUsers.ana())).andExpect(status().isBadRequest());
		mvc.perform(get("/snippets").param("language", "python").with(TestUsers.ana())).andExpect(status().isBadRequest());
	}
}
