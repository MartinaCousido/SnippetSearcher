package com.snippetsearcher.snippets.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	private final UserDirectory users;

	public UserController(UserDirectory users) {
		this.users = users;
	}

	// getUserFriends() de la UI: con quién se puede compartir un snippet (US-7).
	// name filtra por el comienzo del email. Como todo GET, pide read:snippets.
	@GetMapping("/users")
	public UserPage search(
			@RequestParam(defaultValue = "") String name,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "page_size", defaultValue = "10") @Min(1) @Max(100) int pageSize) {
		return users.search(name.strip(), page, pageSize);
	}
}
