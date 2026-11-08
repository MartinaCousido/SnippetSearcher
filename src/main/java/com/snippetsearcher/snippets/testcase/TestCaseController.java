package com.snippetsearcher.snippets.testcase;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/snippets/{snippetId}/tests")
public class TestCaseController {

	private final TestCaseService service;

	public TestCaseController(TestCaseService service) {
		this.service = service;
	}

	// getTestCases() de la UI.
	@GetMapping
	public List<TestCaseResponse> list(@PathVariable UUID snippetId) {
		return service.list(snippetId);
	}

	// postTestCase() de la UI: crea, o cambia si viene el id. Solo el owner.
	@PostMapping
	public TestCaseResponse save(@PathVariable UUID snippetId, @Valid @RequestBody TestCaseRequest request) {
		return service.save(snippetId, request);
	}

	// removeTestCase() de la UI. Solo el owner.
	@DeleteMapping("/{testId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID snippetId, @PathVariable UUID testId) {
		service.delete(snippetId, testId);
	}

	// testSnippet() de la UI. El owner y los usuarios con acceso compartido.
	@PostMapping("/run")
	public RunTestResponse run(@PathVariable UUID snippetId, @RequestBody RunTestRequest request) {
		return service.run(snippetId, request);
	}
}
