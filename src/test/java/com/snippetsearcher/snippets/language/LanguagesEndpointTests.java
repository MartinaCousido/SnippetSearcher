package com.snippetsearcher.snippets.language;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LanguagesEndpointTests {

	@Autowired
	private MockMvc mvc;

	// La extensión es ps: los tests de Cypress de la cátedra suben archivos .ps.
	@Test
	void listsPrintScriptWithItsExtension() throws Exception {
		mvc.perform(get("/languages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].language").value("printscript"))
				.andExpect(jsonPath("$[0].extension").value("ps"));
	}
}
