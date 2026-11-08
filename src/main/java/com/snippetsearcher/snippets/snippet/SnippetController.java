package com.snippetsearcher.snippets.snippet;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

	private final SnippetService service;

	public SnippetController(SnippetService service) {
		this.service = service;
	}

	// createSnippet() de la UI. Responde 200 con el Snippet: es lo que esperan
	// los tests de Cypress de la cátedra.
	@PostMapping
	public SnippetResponse create(@Valid @RequestBody CreateSnippetRequest request) {
		return service.create(request);
	}

	// getSnippetById() de la UI. Un id que no es un UUID responde 400.
	@GetMapping("/{id}")
	public SnippetResponse get(@PathVariable UUID id) {
		return service.get(id);
	}

	// updateSnippetById() de la UI. Solo el owner.
	@PutMapping("/{id}")
	public SnippetResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSnippetRequest request) {
		return service.update(id, request);
	}

	// deleteSnippet() de la UI. Solo el owner.
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		service.delete(id);
	}

	// downloadSnippet() de la UI (US-13): el archivo original o el formateado,
	// como descarga.
	@GetMapping("/{id}/download")
	public ResponseEntity<String> download(@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean formatted) {
		var file = service.download(id, formatted);
		return ResponseEntity.ok()
				.contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}

	// formatSnippet() de la UI: formatea con las reglas del owner, sin guardar.
	@PostMapping("/{id}/format")
	public FormatResponse format(@PathVariable UUID id, @Valid @RequestBody FormatRequest request) {
		return service.format(id, request.content());
	}

	// listSnippetDescriptors() de la UI. relation no lo manda la UI de la
	// cátedra: por defecto, propios y compartidos.
	@GetMapping
	public SnippetPage list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "page_size", defaultValue = "10") @Min(1) @Max(100) int pageSize,
			@RequestParam(required = false) String name,
			@RequestParam(defaultValue = "all") @Pattern(regexp = "owner|shared|all") String relation) {
		return service.list(page, pageSize, name, relation);
	}
}
