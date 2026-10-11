package com.snippetsearcher.snippets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SnippetServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void pruebaQueFallaAPropositoParaElCi() {
		org.junit.jupiter.api.Assertions.fail("Prueba del CI: este test falla a propósito");
	}

}
