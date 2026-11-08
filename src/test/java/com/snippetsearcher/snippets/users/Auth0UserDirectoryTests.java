package com.snippetsearcher.snippets.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import com.snippetsearcher.snippets.security.CurrentUser;

// La Management API de Auth0 se simula con MockRestServiceServer.
class Auth0UserDirectoryTests {

	private static final String AUTH0 = "https://tenant.test";
	private static final String TOKEN = """
			{"access_token": "token-de-la-app", "expires_in": 86400, "token_type": "Bearer"}
			""";

	private MockRestServiceServer server;
	private MutableClock clock;
	private Auth0UserDirectory users;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		clock = new MutableClock(Instant.parse("2026-11-08T12:00:00Z"));
		users = new Auth0UserDirectory(builder, "tenant.test", "id", "secret", new CurrentUser(), clock);
		var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("auth0|ana").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// Como viaja en la URL: con todos los caracteres especiales codificados.
	private static String encoded(String query) {
		return UriUtils.encode(query, StandardCharsets.UTF_8);
	}

	private void expectToken() {
		server.expect(once(), requestTo(AUTH0 + "/oauth/token"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
	}

	@Test
	void searchesByThePrefixOfTheEmailWithoutTheUserWhoAsks() {
		expectToken();
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andExpect(header("Authorization", "Bearer token-de-la-app"))
				.andExpect(queryParam("q", encoded("email:leo* AND NOT user_id:\"auth0|ana\"")))
				.andExpect(queryParam("page", "1"))
				.andExpect(queryParam("per_page", "5"))
				.andRespond(withSuccess("""
						{"total": 6, "users": [{"user_id": "auth0|leo", "email": "leo@example.com"}]}
						""", MediaType.APPLICATION_JSON));

		var page = users.search("Leo", 1, 5);

		assertThat(page.count()).isEqualTo(6);
		assertThat(page.users()).containsExactly(new UserPage.User("auth0|leo", "leo@example.com"));
		server.verify();
	}

	@Test
	void reusesTheTokenUntilItIsAboutToExpire() {
		expectToken();
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andRespond(withSuccess("{\"total\": 0, \"users\": []}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andRespond(withSuccess("{\"total\": 0, \"users\": []}", MediaType.APPLICATION_JSON));

		users.search("", 0, 10);
		users.search("", 0, 10);

		server.verify();
	}

	@Test
	void asksForAllTheEmailsInOneRequestAndRemembersThem() {
		expectToken();
		server.expect(once(), requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andExpect(queryParam("q", encoded("user_id:(\"auth0|leo\" OR \"dev-user\")")))
				.andRespond(withSuccess("""
						[{"user_id": "auth0|leo", "email": "leo@example.com"}]
						""", MediaType.APPLICATION_JSON));

		var first = users.emails(List.of("auth0|leo", "dev-user", "auth0|leo"));
		// La segunda vez no le pregunta a Auth0, ni por el que no existe.
		var second = users.emails(List.of("auth0|leo", "dev-user"));

		assertThat(first).isEqualTo(Map.of("auth0|leo", "leo@example.com"));
		assertThat(second).isEqualTo(first);
		server.verify();
	}

	@Test
	void asksAgainWhenTheEmailIsOld() {
		expectToken();
		var leo = "[{\"user_id\": \"auth0|leo\", \"email\": \"leo@example.com\"}]";
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andRespond(withSuccess(leo, MediaType.APPLICATION_JSON));
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andRespond(withSuccess(leo, MediaType.APPLICATION_JSON));

		users.emails(List.of("auth0|leo"));
		clock.advance(Auth0UserDirectory.EMAIL_TTL.plusSeconds(1));
		users.emails(List.of("auth0|leo"));

		server.verify();
	}

	@Test
	void aUserWithoutEmailIsShownWithItsId() {
		expectToken();
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users")))
				.andRespond(withSuccess("[{\"user_id\": \"google-oauth2|1\"}]", MediaType.APPLICATION_JSON));

		assertThat(users.emails(List.of("google-oauth2|1"))).isEmpty();
		server.verify();
	}

	@Test
	void ifAuth0FailsTheEmailsAreMissingButNothingBreaks() {
		expectToken();
		server.expect(requestTo(Matchers.startsWith(AUTH0 + "/api/v2/users"))).andRespond(withServerError());

		assertThat(users.emails(List.of("auth0|leo"))).isEmpty();
		server.verify();
	}

	@Test
	void escapesTheCharactersOfTheSearchSyntax() {
		assertThat(Auth0UserDirectory.escape("a+b:c* d")).isEqualTo("a\\+b\\:c\\*d");
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(java.time.Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
