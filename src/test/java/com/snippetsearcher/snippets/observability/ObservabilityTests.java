package com.snippetsearcher.snippets.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.snippetsearcher.snippets.security.TestUsers;

// GET /health para los synthetics y el requestId de cada pedido.
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private SystemHealth health;

	@Test
	void theHealthIsPublicAndSaysOnlyUp() throws Exception {
		when(health.isUp()).thenReturn(true);

		mvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void ifSomethingIsDownTheHealthIsServiceUnavailable() throws Exception {
		when(health.isUp()).thenReturn(false);

		mvc.perform(get("/health"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"));
	}

	// Cada respuesta dice su requestId, el mismo que llevan sus logs.
	@Test
	void eachRequestHasARequestIdAndKeepsTheOneItGets() throws Exception {
		var created = mvc.perform(get("/languages").with(TestUsers.ana()))
				.andExpect(header().exists(RequestContextFilter.REQUEST_ID_HEADER))
				.andReturn().getResponse().getHeader(RequestContextFilter.REQUEST_ID_HEADER);
		assertThat(created).hasSize(36);

		mvc.perform(get("/languages").header(RequestContextFilter.REQUEST_ID_HEADER, "synthetic-1").with(TestUsers.ana()))
				.andExpect(header().string(RequestContextFilter.REQUEST_ID_HEADER, "synthetic-1"));
	}
}
