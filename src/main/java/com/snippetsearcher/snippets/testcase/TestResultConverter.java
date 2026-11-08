package com.snippetsearcher.snippets.testcase;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Guarda TestResult en la base con el mismo valor que ve la UI (success, fail).
@Converter(autoApply = true)
public class TestResultConverter implements AttributeConverter<TestResult, String> {

	@Override
	public String convertToDatabaseColumn(TestResult result) {
		return result == null ? null : result.value();
	}

	@Override
	public TestResult convertToEntityAttribute(String value) {
		return value == null ? null : TestResult.fromValue(value);
	}
}
