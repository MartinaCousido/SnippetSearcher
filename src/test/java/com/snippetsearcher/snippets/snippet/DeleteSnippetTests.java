package com.snippetsearcher.snippets.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.snippetsearcher.snippets.clients.Access;
import com.snippetsearcher.snippets.clients.PermissionClient;
import com.snippetsearcher.snippets.clients.SnippetStorage;

// DELETE /snippets/{id} contra la base real. Los otros servicios se simulan.
@SpringBootTest
@AutoConfigureMockMvc
class DeleteSnippetTests {

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
				"dev-user"));
		when(permissions.access(SALUDO)).thenReturn(new Access(true, true));
	}

	@Test
	void theOwnerDeletesTheSnippetItsCodeAndItsPermissions() throws Exception {
		mvc.perform(delete("/snippets/{id}", SALUDO)).andExpect(status().isNoContent());

		assertThat(repository.findById(SALUDO)).isEmpty();
		verify(storage).delete(SALUDO.toString());
		verify(permissions).deleteAll(SALUDO);
	}

	@Test
	void aUserItWasSharedWithCannotDeleteIt() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(true, false));

		mvc.perform(delete("/snippets/{id}", SALUDO)).andExpect(status().isForbidden());

		assertNothingWasDeleted();
	}

	@Test
	void aUserWithoutAccessCannotDeleteIt() throws Exception {
		when(permissions.access(SALUDO)).thenReturn(new Access(false, false));

		mvc.perform(delete("/snippets/{id}", SALUDO)).andExpect(status().isForbidden());

		assertNothingWasDeleted();
	}

	// Primero se busca: si no existe es 404, sin preguntarle a Permisos.
	@Test
	void anUnknownSnippetIsNotFound() throws Exception {
		mvc.perform(delete("/snippets/{id}", UNKNOWN)).andExpect(status().isNotFound());

		verify(permissions, never()).access(any());
		verify(permissions, never()).deleteAll(any());
	}

	// Si no se puede borrar el archivo, el snippet queda como estaba.
	@Test
	void ifTheCodeCannotBeDeletedTheSnippetStays() throws Exception {
		doThrow(new ResourceAccessException("Asset Service caído")).when(storage).delete(anyString());

		mvc.perform(delete("/snippets/{id}", SALUDO)).andExpect(status().isBadGateway());

		assertThat(repository.findById(SALUDO)).isPresent();
		verify(permissions, never()).deleteAll(any());
	}

	// Los permisos sueltos no dan acceso a nada: el snippet se borra igual.
	@Test
	void ifThePermissionsCannotBeDeletedTheSnippetIsDeletedAnyway() throws Exception {
		doThrow(new ResourceAccessException("Permission Service caído")).when(permissions).deleteAll(SALUDO);

		mvc.perform(delete("/snippets/{id}", SALUDO)).andExpect(status().isNoContent());

		assertThat(repository.findById(SALUDO)).isEmpty();
		verify(storage).delete(SALUDO.toString());
	}

	private void assertNothingWasDeleted() {
		assertThat(repository.findById(SALUDO)).isPresent();
		verify(storage, never()).delete(anyString());
		verify(permissions, never()).deleteAll(any());
	}
}
