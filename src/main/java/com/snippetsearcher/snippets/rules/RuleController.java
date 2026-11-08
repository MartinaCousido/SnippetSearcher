package com.snippetsearcher.snippets.rules;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rules")
public class RuleController {

	private final RuleService service;

	public RuleController(RuleService service) {
		this.service = service;
	}

	// getLintingRules() de la UI (US-14).
	@GetMapping("/lint")
	public List<RuleResponse> lint() {
		return service.list(RuleKind.LINT);
	}

	// modifyLintingRule() de la UI.
	@PutMapping("/lint")
	public List<RuleResponse> updateLint(@RequestBody List<RuleRequest> rules) {
		return service.update(RuleKind.LINT, rules);
	}

	// getFormatRules() de la UI (US-11).
	@GetMapping("/format")
	public List<RuleResponse> format() {
		return service.list(RuleKind.FORMAT);
	}

	// modifyFormatRule() de la UI.
	@PutMapping("/format")
	public List<RuleResponse> updateFormat(@RequestBody List<RuleRequest> rules) {
		return service.update(RuleKind.FORMAT, rules);
	}
}
