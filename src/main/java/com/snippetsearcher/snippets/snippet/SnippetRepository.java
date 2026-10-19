package com.snippetsearcher.snippets.snippet;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnippetRepository extends JpaRepository<Snippet, UUID> {
}
