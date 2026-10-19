package com.snippetsearcher.snippets.clients;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class PermissionServiceClientTests {

	private static final UUID SNIPPET_ID = UUID.fromString("7f0c1c9e-3c1a-4c4e-9d3b-2f6b8a1d5e42");

	private MockRestServiceServer server;
	private PermissionServiceClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new PermissionServiceClient(builder, "http://permission-service");
	}

	@Test
	void registersTheOwnerWithoutSendingTheUser() {
		server.expect(requestTo("http://permission-service/permissions/" + SNIPPET_ID + "/owner"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.CREATED));

		client.registerOwner(SNIPPET_ID);

		server.verify();
	}

	@Test
	void aConflictIsReported() {
		server.expect(requestTo("http://permission-service/permissions/" + SNIPPET_ID + "/owner"))
				.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> client.registerOwner(SNIPPET_ID)).isInstanceOf(RestClientResponseException.class);
	}
}
