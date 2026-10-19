package com.snippetsearcher.snippets.snippet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
