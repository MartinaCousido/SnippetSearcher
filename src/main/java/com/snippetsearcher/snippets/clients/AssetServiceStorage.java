package com.snippetsearcher.snippets.clients;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// es la implementación de SnippetStorage
// que guarda cada snippet como un archivo en Azurite: /v1/asset/{container}/{key}.
@Component
public class AssetServiceStorage implements SnippetStorage {

	private static final String ASSET_PATH = "/v1/asset/{container}/{key}";
	private static final MediaType UTF8_TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

	private final RestClient client; // le habla por HTTP al Asset Service con RestClient
	private final String container;

	public AssetServiceStorage(
			RestClient.Builder builder,
			@Value("${asset-service.url}") String baseUrl,
			@Value("${asset-service.container}") String container) {
		this.client = builder.baseUrl(baseUrl).build();
		this.container = container;
	}

	@Override
	public void put(String key, String content) {
		client.put()
				.uri(ASSET_PATH, container, key)
				.contentType(UTF8_TEXT)
				.body(content.getBytes(StandardCharsets.UTF_8))
				.retrieve()
				.toBodilessEntity();
	}

	@Override
	public Optional<String> get(String key) {
		// Se lee en bytes y se decodifica como UTF-8: el Asset Service no dice el
		// charset, y sin esto se romperían los acentos del código.
		byte[] content = client.get()
				.uri(ASSET_PATH, container, key)
				.retrieve()
				.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
				})
				.body(byte[].class);
		return Optional.ofNullable(content).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
	}

	@Override
	public void delete(String key) {
		client.delete()
				.uri(ASSET_PATH, container, key)
				.retrieve()
				.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
				})
				.toBodilessEntity();
	}
}
