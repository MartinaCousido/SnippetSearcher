package com.snippetsearcher.snippets.clients;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Language para PrintScript: le pide todo por HTTP al servicio de PrintScript
// (repo SnippetPrintScript). El nombre del bean es el del lenguaje en
// application.properties.
@Component("printscript")
public class PrintScriptLanguage implements Language {

	private final RestClient client;

	public PrintScriptLanguage(RestClient.Builder builder, @Value("${printscript-service.url}") String baseUrl) {
		this.client = builder.baseUrl(baseUrl).build();
	}

	@Override
	public List<Diagnostic> validate(String code, String version) {
		var response = client.post()
				.uri("/validate")
				.body(new ValidateRequest(code, version))
				.retrieve()
				.body(ValidateResponse.class);
		return response == null ? List.of() : response.diagnostics();
	}

	private record ValidateRequest(String code, String version) {
	}

	private record ValidateResponse(List<Diagnostic> diagnostics) {
	}
}
