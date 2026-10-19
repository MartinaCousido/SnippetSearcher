package com.snippetsearcher.snippets.snippet;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
