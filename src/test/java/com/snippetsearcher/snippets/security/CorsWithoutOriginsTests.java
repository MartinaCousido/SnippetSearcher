package com.snippetsearcher.snippets.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Sin CORS_ALLOWED_ORIGINS, no se acepta ningún origin externo.
@SpringBootTest(properties = "cors.allowed-origins=")
@AutoConfigureMockMvc
class CorsWithoutOriginsTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void rejectsEveryOrigin() throws Exception {
		mvc.perform(options("/snippets")
						.header("Origin", "https://snippetsearcher-dev.duckdns.org")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	// Detrás del proxy, la UI y la API están en el mismo origin: el navegador
	// manda un Origin igual al Host del pedido, y eso no es CORS. Sin la
	// variable, la UI de la VM tiene que seguir andando.
	@Test
	void sameOriginIsNotAffected() throws Exception {
		mvc.perform(get("/languages")
						.header("Host", "snippetsearcher-dev.duckdns.org")
						.header("Origin", "http://snippetsearcher-dev.duckdns.org"))
				.andExpect(status().isOk());
	}
}
