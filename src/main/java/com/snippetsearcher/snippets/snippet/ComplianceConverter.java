package com.snippetsearcher.snippets.snippet;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Guarda Compliance en la base con el mismo valor que ve la UI (pending, ...).
@Converter(autoApply = true)
public class ComplianceConverter implements AttributeConverter<Compliance, String> {

	@Override
	public String convertToDatabaseColumn(Compliance compliance) {
		return compliance.value();
	}

	@Override
	public Compliance convertToEntityAttribute(String value) {
		return Compliance.fromValue(value);
	}
}
