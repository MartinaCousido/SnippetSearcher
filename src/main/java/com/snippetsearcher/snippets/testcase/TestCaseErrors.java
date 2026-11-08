package com.snippetsearcher.snippets.testcase;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TestCaseErrors {

	@ExceptionHandler(TestCaseNotFoundException.class)
	public ProblemDetail notFound(TestCaseNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
