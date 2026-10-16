package com.snippetsearcher.snippets.clients;

import java.util.Optional;

// Guarda y entrega el código de los snippets.
// La clave es el id del snippet.
public interface SnippetStorage {

	// Guarda el código. Si ya había un archivo con esa clave, lo pisa.
	void put(String key, String content);

	// Devuelve el código, o vacío si no hay un archivo con esa clave.
	Optional<String> get(String key);

	// Borra el archivo. Si no existía, no hace nada: así se puede llamar
	// sin miedo al deshacer una creación que falló a mitad de camino.
	void delete(String key);
}
