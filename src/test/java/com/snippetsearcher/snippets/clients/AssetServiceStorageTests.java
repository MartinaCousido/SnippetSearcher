package com.snippetsearcher.snippets.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Prueba el cliente contra un Asset Service simulado: verifica qué pedido HTTP
// arma y cómo interpreta cada respuesta, sin levantar el servicio real.
class AssetServiceStorageTests {

	private static final String URL = "http://asset-service/v1/asset/snippets/42";
	private static final String CODE = "let saludo: string = \"¡Hola, Martín!\";\nprintln(saludo);";

	private MockRestServiceServer server;
	private AssetServiceStorage storage;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		storage = new AssetServiceStorage(builder, "http://asset-service", "snippets");
	}

	@Test
	void putSendsTheCodeAsUtf8ToTheSnippetKey() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().bytes(CODE.getBytes(StandardCharsets.UTF_8)))
				.andRespond(withStatus(HttpStatus.CREATED));

		storage.put("42", CODE);

		server.verify();
	}

	@Test
	void putOverAnExistingFileIsNotAnError() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess());

		storage.put("42", CODE);

		server.verify();
	}

	@Test
	void getReturnsTheStoredCodeKeepingAccents() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(CODE.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM));

		assertEquals(Optional.of(CODE), storage.get("42"));
	}

	@Test
	void getOfAMissingFileIsEmpty() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertTrue(storage.get("42").isEmpty());
	}

	@Test
	void deleteRemovesTheFile() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

		storage.delete("42");

		server.verify();
	}

	@Test
	void deleteOfAMissingFileIsNotAnError() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		storage.delete("42");

		server.verify();
	}

	@Test
	void aServerErrorIsReportedAsAnException() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThrows(RestClientResponseException.class, () -> storage.get("42"));
	}
}
