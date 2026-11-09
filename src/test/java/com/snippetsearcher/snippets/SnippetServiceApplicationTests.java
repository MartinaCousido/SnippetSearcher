package com.snippetsearcher.snippets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.snippetsearcher.snippets.snippet.SnippetService;

@SpringBootTest
class SnippetServiceApplicationTests {

	@Autowired
	private ApplicationContext context;

	// El servicio arranca con su configuración y el servicio de snippets queda listo.
	@Test
	void contextLoads() {
		assertNotNull(context.getBean(SnippetService.class));
	}

}
