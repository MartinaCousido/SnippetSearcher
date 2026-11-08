package com.snippetsearcher.snippets.snippet;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hamcrest.Matchers;
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
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.users.UserDirectory;

// GET /snippets/{id}/download (US-13), contra la base real. Los otros
// servicios se simulan.
@SpringBootTest
@AutoConfigureMockMvc
class DownloadSnippetTests {

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

	@MockitoBean
	private UserDirectory users;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		repository.save(new Snippet(SALUDO, "saludo por edad", "", "printscript", "1.1", SALUDO.toString(),
				TestUsers.ANA));
		when(permissions.access(SALUDO)).thenReturn(new Access(true, false));
		when(storage.get(SALUDO.toString())).thenReturn(Optional.of("let a:number=1;"));
		when(storage.get(SALUDO + ".formatted")).thenReturn(Optional.of("let a: number = 1;\n"));
	}

	private void formatted() {
		var snippet = repository.findById(SALUDO).orElseThrow();
		snippet.recordFormatted(snippet.requestFormat(), SALUDO + ".formatted");
		repository.save(snippet);
	}

	@Test
	void downloadsTheOriginalAsAFile() throws Exception {
		mvc.perform(get("/snippets/{id}/download", SALUDO).with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
				.andExpect(header().string("Content-Disposition",
						Matchers.containsString("saludo%20por%20edad.ps")))
				.andExpect(content().string("let a:number=1;"));
	}

	@Test
	void downloadsTheFormattedVersionThatWasSaved() throws Exception {
		formatted();

		mvc.perform(get("/snippets/{id}/download", SALUDO).param("formatted", "true").with(TestUsers.ana()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						Matchers.containsString("saludo%20por%20edad-formateado.ps")))
				.andExpect(content().string("let a: number = 1;\n"));
	}

	@Test
	void withoutAFormattedVersionItSaysSo() throws Exception {
		mvc.perform(get("/snippets/{id}/download", SALUDO).param("formatted", "true").with(TestUsers.ana()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Este snippet todavía no tiene versión formateada"));
	}

	@Test
	void withoutAccessItIsForbiddenAndAMissingSnippetIsNotFound() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		mvc.perform(get("/snippets/{id}/download", SALUDO).with(TestUsers.ana())).andExpect(status().isForbidden());
		mvc.perform(get("/snippets/{id}/download", UNKNOWN).with(TestUsers.ana())).andExpect(status().isNotFound());
		mvc.perform(get("/snippets/{id}/download", SALUDO)).andExpect(status().isUnauthorized());
	}
}
