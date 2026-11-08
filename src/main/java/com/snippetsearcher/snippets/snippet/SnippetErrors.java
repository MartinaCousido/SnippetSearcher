package com.snippetsearcher.snippets.snippet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import com.snippetsearcher.snippets.clients.Diagnostic;
import com.snippetsearcher.snippets.language.UnsupportedLanguageException;

// Cómo se responde cada error. Nunca un 500 por una excepción conocida.
@RestControllerAdvice
public class SnippetErrors {

	private static final Logger log = LoggerFactory.getLogger(SnippetErrors.class);

	// El código no es válido: 400 con la regla, la línea y la columna (US-1).
	@ExceptionHandler(InvalidSnippetException.class)
	public ResponseEntity<InvalidSnippetResponse> invalidSnippet(InvalidSnippetException e) {
		log.info("código inválido: {}", e.diagnostics().stream().map(d -> d.rule()).toList());
		return ResponseEntity.badRequest().body(new InvalidSnippetResponse(e.diagnostics()));
	}

	@ExceptionHandler(EmptySnippetException.class)
	public ProblemDetail emptySnippet(EmptySnippetException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(UnsupportedLanguageException.class)
	public ProblemDetail unsupportedLanguage(UnsupportedLanguageException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(SnippetNotFoundException.class)
	public ProblemDetail notFound(SnippetNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(FormattedVersionNotFoundException.class)
	public ProblemDetail noFormattedVersion(FormattedVersionNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(SnippetAccessDeniedException.class)
	public ProblemDetail accessDenied(SnippetAccessDeniedException e) {
		log.info("acceso denegado: {}", e.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}

	// Falló otro servicio (PrintScript, Asset Service o Permission Service).
	@ExceptionHandler(RestClientException.class)
	public ProblemDetail serviceFailed(RestClientException e) {
		// Un servicio del que dependemos no respondió bien: es lo que hay que ver.
		log.error("falló un servicio del que depende Snippet Service: {}", e.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"Un servicio del que depende Snippet Service no respondió bien. Probá de nuevo.");
	}

	public record InvalidSnippetResponse(List<Diagnostic> diagnostics) {
	}
}
