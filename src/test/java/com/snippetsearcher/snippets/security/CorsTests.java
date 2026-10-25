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

// CORS con los origins de dev. Se prueba sobre GET /languages, que no llama a
// otros servicios.
@SpringBootTest(properties = "cors.allowed-origins=https://snippetsearcher-dev.duckdns.org, http://localhost:5173")
@AutoConfigureMockMvc
class CorsTests {

	private static final String DEV = "https://snippetsearcher-dev.duckdns.org";
	private static final String AJENO = "https://evil.com";

	@Autowired
	private MockMvc mvc;

	@Test
	void preflightFromAllowedOriginGetsCorsHeaders() throws Exception {
		mvc.perform(options("/snippets")
						.header("Origin", DEV)
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", DEV))
				.andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
				.andExpect(header().string("Access-Control-Allow-Headers", "Authorization, Content-Type"));
	}

	// Los espacios después de las comas de la variable no cuentan.
	@Test
	void acceptsEveryOriginOfTheList() throws Exception {
		mvc.perform(get("/languages").header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void preflightFromOtherOriginGetsNoCorsHeaders() throws Exception {
		mvc.perform(options("/snippets")
						.header("Origin", AJENO)
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void preflightWithHeaderNotInTheListIsRejected() throws Exception {
		mvc.perform(options("/snippets")
						.header("Origin", DEV)
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "X-Otro"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void getFromAllowedOriginGetsAllowOrigin() throws Exception {
		mvc.perform(get("/languages").header("Origin", DEV))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", DEV));
	}

	@Test
	void getWithoutOriginIsNotAffected() throws Exception {
		mvc.perform(get("/languages"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
