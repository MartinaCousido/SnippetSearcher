package com.snippetsearcher.snippets.analysis;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.snippetsearcher.snippets.clients.Language;
import com.snippetsearcher.snippets.clients.SnippetStorage;
import com.snippetsearcher.snippets.language.LanguageCatalog;
import com.snippetsearcher.snippets.security.TestUsers;
import com.snippetsearcher.snippets.snippet.Snippet;
import com.snippetsearcher.snippets.snippet.SnippetRepository;

// Re-validar con cada versión nueva del parser (#93). En los tests el
// consumidor está apagado, así que la revisión se arma a mano.
@SpringBootTest
class ParserVersionCheckTests {

	private static final UUID VIEJO = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID AL_DIA = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SIN_VERSION = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID PENDIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

	@Autowired
	private SnippetRepository snippets;

	@Autowired
	private LanguageCatalog languages;

	@Autowired
	private ParserVersions versions;

	@Autowired
	private AnalysisRequests requests;

	@MockitoBean
	private AnalysisQueue queue;

	@MockitoBean(name = "printscript")
	private Language printScript;

	@MockitoBean
	private SnippetStorage storage;

	private ParserVersionCheck check;

	@BeforeEach
	void setUp() {
		snippets.deleteAll();
		analyzed(VIEJO, "0.3.1");
		analyzed(AL_DIA, "0.3.2");
		analyzed(SIN_VERSION, null);
		snippets.save(new Snippet(PENDIENTE, "pendiente", "", "printscript", "1.1", PENDIENTE.toString(),
				TestUsers.ANA));
		check = new ParserVersionCheck(languages, versions, snippets, requests);
	}

	// Un snippet ya analizado (compliant), con esa versión.
	private void analyzed(UUID id, String version) {
		var snippet = new Snippet(id, id.toString(), "", "printscript", "1.1", id.toString(), TestUsers.ANA);
		snippet.recordLint(snippet.requestLint(), List.of(), version);
		snippets.save(snippet);
	}

	@Test
	void snippetsAnalyzedWithAnotherVersionAreAnalyzedAgain() {
		when(printScript.libraryVersion()).thenReturn("0.3.2");

		check.check();

		verify(queue).publish(eq(AnalysisTask.LINT), eq(VIEJO), anyInt());
		verify(queue).publish(eq(AnalysisTask.FORMAT), eq(VIEJO), anyInt());
		verify(queue).publish(eq(AnalysisTask.LINT), eq(SIN_VERSION), anyInt());
		verify(queue, never()).publish(eq(AnalysisTask.LINT), eq(AL_DIA), anyInt());
		// Ya tiene un análisis pedido: no se pide de nuevo.
		verify(queue, never()).publish(eq(AnalysisTask.LINT), eq(PENDIENTE), anyInt());
	}

	@Test
	void ifTheVersionIsUnknownNothingIsAsked() {
		when(printScript.libraryVersion()).thenThrow(new IllegalStateException("PrintScript no responde"));

		check.check();

		verify(queue, never()).publish(eq(AnalysisTask.LINT), eq(VIEJO), anyInt());
	}
}
