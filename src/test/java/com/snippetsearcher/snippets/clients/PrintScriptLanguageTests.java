package com.snippetsearcher.snippets.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.snippetsearcher.snippets.clients.Diagnostic.Position;
import com.snippetsearcher.snippets.clients.Diagnostic.Range;

// Prueba el cliente contra un servicio de PrintScript simulado: qué pedido
// arma y cómo lee los diagnósticos.
class PrintScriptLanguageTests {

	private MockRestServiceServer server;
	private PrintScriptLanguage language;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		language = new PrintScriptLanguage(builder, "http://printscript-service");
	}

	@Test
	void sendsTheCodeAndVersionAndReadsTheDiagnostics() {
		server.expect(requestTo("http://printscript-service/validate"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"code\": \"println(1)\", \"version\": \"1.1\"}"))
				.andRespond(withSuccess("""
						{"diagnostics": [{"rule": "MISSING_SEMICOLON", "message": "Expected ';'",
						  "range": {"start": {"line": 1, "column": 11}, "end": {"line": 1, "column": 11}}}]}
						""", MediaType.APPLICATION_JSON));

		var diagnostics = language.validate("println(1)", "1.1");

		assertThat(diagnostics).containsExactly(new Diagnostic("MISSING_SEMICOLON", "Expected ';'",
				new Range(new Position(1, 11), new Position(1, 11))));
		server.verify();
	}

	@Test
	void validCodeHasNoDiagnostics() {
		server.expect(requestTo("http://printscript-service/validate"))
				.andRespond(withSuccess("{\"diagnostics\": []}", MediaType.APPLICATION_JSON));

		assertThat(language.validate("println(1);", "1.1")).isEmpty();
	}
}
