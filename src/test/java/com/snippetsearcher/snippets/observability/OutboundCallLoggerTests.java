package com.snippetsearcher.snippets.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Cada llamada a otro servicio queda en el log, sin la query ni los headers.
@ExtendWith(OutputCaptureExtension.class)
class OutboundCallLoggerTests {

	@Test
	void logsWhoWasCalledWhatItAnsweredAndHowLongItTook(CapturedOutput output) {
		var builder = RestClient.builder().requestInterceptor(new OutboundCallLogger());
		var server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://permission-service/permissions/snippets?relation=all"))
				.andRespond(withSuccess());
		server.expect(requestTo("http://asset-service/v1/asset/snippets/1")).andRespond(withServerError());
		var client = builder.build();

		client.get().uri("http://permission-service/permissions/snippets?relation=all").header("Authorization", "Bearer secreto")
				.retrieve().toBodilessEntity();
		try {
			client.get().uri("http://asset-service/v1/asset/snippets/1").retrieve().toBodilessEntity();
		} catch (RuntimeException expected) {
			// 500: el log lo registra igual.
		}

		assertThat(output).contains("llamada GET permission-service/permissions/snippets → 200 en");
		assertThat(output).contains("llamada GET asset-service/v1/asset/snippets/1 → 500 en");
		assertThat(output).doesNotContain("relation=all").doesNotContain("secreto");
	}
}
