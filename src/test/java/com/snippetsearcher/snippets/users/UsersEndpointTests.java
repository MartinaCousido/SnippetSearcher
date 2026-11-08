package com.snippetsearcher.snippets.users;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.snippetsearcher.snippets.security.TestUsers;

// GET /users: la búsqueda de usuarios para compartir (US-7). Auth0 se simula.
@SpringBootTest
@AutoConfigureMockMvc
class UsersEndpointTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UserDirectory users;

	@Test
	void searchesByEmailWithTheFormatOfTheUi() throws Exception {
		when(users.search("leo", 0, 5)).thenReturn(
				new UserPage(0, 5, 1, List.of(new UserPage.User("auth0|leo", "leo@example.com"))));

		mvc.perform(get("/users").param("name", " leo ").param("page", "0").param("page_size", "5")
						.with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.page_size").value(5))
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.users[0].id").value("auth0|leo"))
				.andExpect(jsonPath("$.users[0].name").value("leo@example.com"));
	}

	@Test
	void withoutANameListsEveryone() throws Exception {
		when(users.search("", 0, 10)).thenReturn(new UserPage(0, 10, 0, List.of()));

		mvc.perform(get("/users").with(TestUsers.ana())).andExpect(status().isOk());
		verify(users).search("", 0, 10);
	}

	@Test
	void ifAuth0DoesNotRespondItIsABadGateway() throws Exception {
		when(users.search("", 0, 10)).thenThrow(new ResourceAccessException("Auth0 no responde"));

		mvc.perform(get("/users").with(TestUsers.ana())).andExpect(status().isBadGateway());
	}

	@Test
	void needsReadSnippets() throws Exception {
		mvc.perform(get("/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/users").with(TestUsers.user("auth0|sin-rol", ""))).andExpect(status().isForbidden());
		verifyNoInteractions(users);
	}

	@Test
	void rejectsAPageSizeOutOfRange() throws Exception {
		mvc.perform(get("/users").param("page_size", "101").with(TestUsers.ana()))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(users);
	}
}
