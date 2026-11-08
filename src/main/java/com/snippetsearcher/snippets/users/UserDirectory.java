package com.snippetsearcher.snippets.users;

import java.util.Collection;
import java.util.Map;

// Los usuarios del sistema (módulo User). Viven en Auth0: nuestras bases
// guardan solo su id, el "sub" del token (ver Módulo User en Decisiones
// técnicas).
public interface UserDirectory {

	// Busca usuarios por el comienzo del email, sin distinguir mayúsculas, para
	// compartir un snippet (US-7). Nunca incluye a quien pide. page empieza en 0.
	UserPage search(String email, int page, int pageSize);

	// El email de cada usuario, para mostrar el autor de un snippet. Los ids
	// que no se encuentran, o si Auth0 no responde, no están en el resultado:
	// nunca falla.
	Map<String, String> emails(Collection<String> userIds);
}
