package com.snippetsearcher.snippets.users;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.snippetsearcher.snippets.security.CurrentUser;

// UserDirectory con la Management API de Auth0. Entra con la app
// snippetsearcher-users-reader, que solo tiene read:users (se crea con Pulumi
// en SnippetSearcher-infra).
@Component
public class Auth0UserDirectory implements UserDirectory {

	private static final Logger log = LoggerFactory.getLogger(Auth0UserDirectory.class);

	// Cuánto se recuerda el email de un usuario. Auth0 limita los pedidos por
	// segundo: así el listado no le pregunta en cada página.
	static final Duration EMAIL_TTL = Duration.ofMinutes(10);

	// El token de la Management API se pide de nuevo un poco antes de que venza.
	private static final Duration TOKEN_MARGIN = Duration.ofMinutes(1);

	// Caracteres con significado en las búsquedas de Auth0 (sintaxis de Lucene).
	private static final String SPECIAL = "+-&|!(){}[]^\"~*?:\\/";

	private final RestClient client;
	private final String domain;
	private final String clientId;
	private final String clientSecret;
	private final CurrentUser currentUser;
	private final Clock clock;

	private final Map<String, Cached> emailCache = new ConcurrentHashMap<>();
	private volatile Token token;

	@Autowired
	public Auth0UserDirectory(RestClient.Builder builder, @Value("${auth0.domain}") String domain,
			@Value("${auth0.users.client-id}") String clientId,
			@Value("${auth0.users.client-secret}") String clientSecret, CurrentUser currentUser) {
		this(builder, domain, clientId, clientSecret, currentUser, Clock.systemUTC());
	}

	Auth0UserDirectory(RestClient.Builder builder, String domain, String clientId, String clientSecret,
			CurrentUser currentUser, Clock clock) {
		this.client = builder.baseUrl("https://" + domain).build();
		this.domain = domain;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Override
	public UserPage search(String email, int page, int pageSize) {
		var notMe = "NOT user_id:\"" + currentUser.id() + "\"";
		var query = email.isEmpty() ? notMe : "email:" + escape(email.toLowerCase(Locale.ROOT)) + "* AND " + notMe;
		var found = client.get()
				.uri(uri -> uri.path("/api/v2/users")
						.queryParam("q", "{q}")
						.queryParam("search_engine", "v3")
						.queryParam("fields", "user_id,email")
						.queryParam("include_totals", "true")
						.queryParam("sort", "email:1")
						.queryParam("page", page)
						.queryParam("per_page", pageSize)
						.build(query))
				.headers(headers -> headers.setBearerAuth(accessToken()))
				.retrieve()
				.body(Auth0UserPage.class);
		if (found == null) {
			return new UserPage(page, pageSize, 0, List.of());
		}
		var users = found.users().stream()
				.peek(user -> remember(user.userId(), user.email()))
				.map(user -> new UserPage.User(user.userId(), user.email() == null ? user.userId() : user.email()))
				.toList();
		return new UserPage(page, pageSize, found.total(), users);
	}

	@Override
	public Map<String, String> emails(Collection<String> userIds) {
		var now = clock.instant();
		var result = new HashMap<String, String>();
		var missing = userIds.stream()
				.distinct()
				.filter(id -> {
					var cached = emailCache.get(id);
					if (cached == null || cached.expiresAt().isBefore(now)) {
						return true;
					}
					cached.email().ifPresent(email -> result.put(id, email));
					return false;
				})
				.toList();
		if (missing.isEmpty()) {
			return result;
		}
		try {
			// Un usuario puede no tener email (por ejemplo, si entró con una red
			// social que no lo comparte): se muestra su id.
			for (var user : fetch(missing)) {
				if (user.email() != null) {
					result.put(user.userId(), user.email());
				}
			}
			// Los que Auth0 no conoce (por ejemplo, dev-user) también se
			// recuerdan, para no volver a preguntar por ellos.
			missing.forEach(id -> remember(id, result.get(id)));
		} catch (RestClientException e) {
			log.warn("Auth0 no respondió los emails de {} usuarios: se muestra su id", missing.size(), e);
		}
		return result;
	}

	// Un solo pedido por todos los ids (como mucho 100, el tamaño máximo de
	// una página de snippets).
	private List<Auth0User> fetch(List<String> userIds) {
		var query = userIds.stream()
				.map(id -> "\"" + id.replace("\"", "") + "\"")
				.collect(Collectors.joining(" OR ", "user_id:(", ")"));
		var found = client.get()
				.uri(uri -> uri.path("/api/v2/users")
						.queryParam("q", "{q}")
						.queryParam("search_engine", "v3")
						.queryParam("fields", "user_id,email")
						.queryParam("per_page", userIds.size())
						.build(query))
				.headers(headers -> headers.setBearerAuth(accessToken()))
				.retrieve()
				.body(Auth0User[].class);
		return found == null ? List.of() : List.of(found);
	}

	private void remember(String userId, String email) {
		emailCache.put(userId, new Cached(Optional.ofNullable(email), clock.instant().plus(EMAIL_TTL)));
	}

	// El token de la app para la Management API (client credentials). Se
	// reusa hasta que está por vencer.
	private String accessToken() {
		var current = token;
		if (current != null && current.expiresAt().isAfter(clock.instant().plus(TOKEN_MARGIN))) {
			return current.value();
		}
		var response = client.post()
				.uri("/oauth/token")
				.body(Map.of(
						"grant_type", "client_credentials",
						"client_id", clientId,
						"client_secret", clientSecret,
						"audience", "https://" + domain + "/api/v2/"))
				.retrieve()
				.body(TokenResponse.class);
		if (response == null || response.accessToken() == null) {
			throw new RestClientException("Auth0 no devolvió un token para la Management API");
		}
		token = new Token(response.accessToken(), clock.instant().plusSeconds(response.expiresIn()));
		return token.value();
	}

	static String escape(String text) {
		var escaped = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (SPECIAL.indexOf(c) >= 0) {
				escaped.append('\\');
			}
			if (!Character.isWhitespace(c)) {
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private record Token(String value, Instant expiresAt) {
	}

	private record Cached(Optional<String> email, Instant expiresAt) {
	}

	record TokenResponse(@JsonProperty("access_token") String accessToken,
			@JsonProperty("expires_in") long expiresIn) {
	}

	record Auth0User(@JsonProperty("user_id") String userId, String email) {
	}

	record Auth0UserPage(long total, List<Auth0User> users) {
	}
}
