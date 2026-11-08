package com.snippetsearcher.snippets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Revisa cada tanto los mensajes pendientes de la cola (analysis/).
@EnableScheduling
public class SnippetServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnippetServiceApplication.class, args);
	}

}
