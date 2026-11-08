package com.snippetsearcher.snippets.snippet;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnippetRepository extends JpaRepository<Snippet, UUID> {

	// Los snippets con esos ids cuyo nombre contiene el texto, sin distinguir
	// mayúsculas.
	Page<Snippet> findByIdInAndNameContainingIgnoreCase(Collection<UUID> ids, String name, Pageable pageable);
}
