package com.snippetsearcher.snippets.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sun.net.httpserver.HttpServer;

// SystemHealth contra la base y Redis reales, y los otros servicios simulados
// con un servidor HTTP local: uno caído alcanza para que el sistema esté DOWN.
@SpringBootTest
class SystemHealthTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redis;

	private HttpServer server;
	private int statusOfPrintScript;

	@BeforeEach
	void setUp() throws IOException {
		statusOfPrintScript = 200;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/permission/actuator/health", exchange -> respond(exchange, 200));
		server.createContext("/printscript/actuator/health", exchange -> respond(exchange, statusOfPrintScript));
		server.createContext("/asset/health", exchange -> respond(exchange, 200));
		server.start();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

	private SystemHealth health() {
		var base = "http://127.0.0.1:" + server.getAddress().getPort();
		return new SystemHealth(jdbc, redis, base + "/permission", base + "/printscript", base + "/asset/health");
	}

	@Test
	void whenEverythingAnswersItIsUp() {
		assertThat(health().isUp()).isTrue();
	}

	@Test
	void aServiceThatFailsMakesItDown() {
		statusOfPrintScript = 503;

		assertThat(health().isUp()).isFalse();
	}

	@Test
	void aServiceThatIsNotThereMakesItDown() {
		var base = "http://127.0.0.1:" + server.getAddress().getPort();
		var health = new SystemHealth(jdbc, redis, base + "/permission", base + "/printscript",
				"http://127.0.0.1:1/no-esta");

		assertThat(health.isUp()).isFalse();
	}
}
