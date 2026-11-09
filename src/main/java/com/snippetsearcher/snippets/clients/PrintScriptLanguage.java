package com.snippetsearcher.snippets.clients;

import java.net.URI;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import tools.jackson.databind.json.JsonMapper;

// Language para PrintScript: le pide todo por HTTP al servicio de PrintScript
// (repo SnippetPrintScript). El nombre del bean es el del lenguaje en
// application.properties.
@Component("printscript")
public class PrintScriptLanguage implements Language {

	private final RestClient client;
	private final WebSocketClient webSocketClient = new StandardWebSocketClient();
	private final URI executeUri;
	private final JsonMapper json;

	public PrintScriptLanguage(RestClient.Builder builder, @Value("${printscript-service.url}") String baseUrl,
			JsonMapper json) {
		this.client = builder.baseUrl(baseUrl).build();
		// El WebSocket /execute está en el mismo servicio: http → ws.
		this.executeUri = URI.create(baseUrl.replaceFirst("^http", "ws") + "/execute");
		this.json = json;
	}

	@Override
	public String libraryVersion() {
		var response = client.get().uri("/version").retrieve().body(VersionResponse.class);
		return response == null ? null : response.library();
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

	@Override
	public Evaluation evaluate(String code, String version, List<String> inputs) {
		var response = client.post()
				.uri("/evaluate")
				.body(new EvaluateRequest(code, version, inputs))
				.retrieve()
				.body(EvaluateResponse.class);
		if (response == null) {
			return new Evaluation(List.of(), List.of());
		}
		return new Evaluation(nullToEmpty(response.outputs()), nullToEmpty(response.diagnostics()));
	}

	@Override
	public RuleCatalog rules() {
		var catalog = client.get().uri("/rules").retrieve().body(RuleCatalog.class);
		return catalog == null ? new RuleCatalog(List.of(), List.of()) : catalog;
	}

	@Override
	public List<Diagnostic> lint(String code, String version, Map<String, Object> rules) {
		var response = client.post()
				.uri("/lint")
				.body(new RulesRequest(code, version, rules))
				.retrieve()
				.body(LintResponse.class);
		return response == null ? List.of() : nullToEmpty(response.diagnostics());
	}

	@Override
	public Formatted format(String code, String version, Map<String, Object> rules) {
		var response = client.post()
				.uri("/format")
				.body(new RulesRequest(code, version, rules))
				.retrieve()
				.body(Formatted.class);
		return response == null ? new Formatted(code, List.of())
				: new Formatted(response.content(), nullToEmpty(response.diagnostics()));
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private record ValidateRequest(String code, String version) {
	}

	private record RulesRequest(String code, String version, Map<String, Object> rules) {
	}

	private record LintResponse(List<Diagnostic> diagnostics) {
	}

	private record EvaluateRequest(String code, String version, List<String> inputs) {
	}

	private record EvaluateResponse(List<String> outputs, List<Diagnostic> diagnostics) {
	}

	private record ValidateResponse(List<Diagnostic> diagnostics) {
	}

	@Override
	public LiveExecution executeLive(String code, String version, List<String> inputs, LiveListener listener) {
		return PrintScriptLiveExecution.start(webSocketClient, executeUri, json, code, version, inputs, listener);
	}

	private record VersionResponse(String library) {
	}
}
