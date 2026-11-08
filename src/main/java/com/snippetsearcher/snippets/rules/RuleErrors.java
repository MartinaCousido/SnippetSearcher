package com.snippetsearcher.snippets.rules;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RuleErrors {

	// Una regla que no existe, un valor que no sirve o dos reglas opuestas.
	@ExceptionHandler(InvalidRulesException.class)
	public ProblemDetail invalidRules(InvalidRulesException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
