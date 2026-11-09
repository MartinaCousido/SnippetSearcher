package com.snippetsearcher.snippets.snippet;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

// JpaSpecificationExecutor: el listado (US-5) arma los filtros según lo que
// se pida (ver SnippetQuery).
public interface SnippetRepository extends JpaRepository<Snippet, UUID>, JpaSpecificationExecutor<Snippet> {

	// Los snippets de un usuario: se vuelven a lintear o formatear cuando cambia
	// sus reglas.
	@Query("select s.id from Snippet s where s.author = :author")
	List<UUID> findIdsByAuthor(@Param("author") String author);

	// Los que nunca se lintearon o formatearon: los que existían antes del
	// lint en segundo plano (#23).
	@Query("select s.id from Snippet s where s.lintRevision = 0 or s.formatRevision = 0")
	List<UUID> findIdsNeverAnalyzed();
}
