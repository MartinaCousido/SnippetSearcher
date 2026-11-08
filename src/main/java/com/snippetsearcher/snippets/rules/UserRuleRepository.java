package com.snippetsearcher.snippets.rules;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRuleRepository extends JpaRepository<UserRule, UserRule.Key> {

	List<UserRule> findByUserIdAndKind(String userId, String kind);

	void deleteByUserIdAndKind(String userId, String kind);
}
