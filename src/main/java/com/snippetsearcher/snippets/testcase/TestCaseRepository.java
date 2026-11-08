package com.snippetsearcher.snippets.testcase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

	List<TestCase> findBySnippetIdOrderByCreatedAtAscIdAsc(UUID snippetId);

	Optional<TestCase> findByIdAndSnippetId(UUID id, UUID snippetId);
}
