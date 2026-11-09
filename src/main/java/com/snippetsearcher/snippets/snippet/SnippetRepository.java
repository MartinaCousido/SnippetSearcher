package com.snippetsearcher.snippets.snippet;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface SnippetRepository extends JpaRepository<Snippet, UUID> {

	// Los snippets con esos ids cuyo nombre contiene el texto, sin distinguir
	// mayúsculas.
	Page<Snippet> findByIdInAndNameContainingIgnoreCase(Collection<UUID> ids, String name, Pageable pageable);

	// Los snippets de un usuario: se vuelven a lintear o formatear cuando cambia
	// sus reglas.
	@Query("select s.id from Snippet s where s.author = :author")
	List<UUID> findIdsByAuthor(@Param("author") String author);

	// Los de un lenguaje que no se analizaron con esta versión de su librería
	// (#93). Los que están pendientes ya tienen un análisis pedido: no se piden
	// de nuevo.
	@Query("select s.id from Snippet s where s.language = :language and s.compliance <> :pending"
			+ " and (s.analyzedWith is null or s.analyzedWith <> :version)")
	List<UUID> findIdsAnalyzedWithOtherVersion(@Param("language") String language, @Param("version") String version,
			@Param("pending") Compliance pending);

	// Los que nunca se lintearon o formatearon: los que existían antes del
	// lint en segundo plano (#23).
	@Query("select s.id from Snippet s where s.lintRevision = 0 or s.formatRevision = 0")
	List<UUID> findIdsNeverAnalyzed();
}
