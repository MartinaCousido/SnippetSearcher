package com.snippetsearcher.snippets.testcase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.snippetsearcher.snippets.language.LanguageCatalog.ResolvedLanguage;

// Corre un test: ejecuta el código con sus inputs y compara lo que imprimió
// con los outputs esperados, exactos y en orden. Un error al ejecutar es fail
// (ver "Tests de un snippet" en Decisiones técnicas).
@Component
public class TestRunner {

	public TestResult run(ResolvedLanguage language, String code, List<String> inputs, List<String> expected) {
		var evaluation = language.language().evaluate(code, language.version(), inputs);
		if (evaluation.failed() || !evaluation.outputs().equals(expected)) {
			return TestResult.FAIL;
		}
		return TestResult.SUCCESS;
	}
}
