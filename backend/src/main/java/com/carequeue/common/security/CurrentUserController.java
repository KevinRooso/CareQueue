package com.carequeue.common.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "current-user", description = "The authenticated user")
public class CurrentUserController {

	private final RoleHierarchy roleHierarchy;

	public CurrentUserController(RoleHierarchy roleHierarchy) {
		this.roleHierarchy = roleHierarchy;
	}

	public record MeResponse(String username, List<String> roles) {
	}

	@GetMapping("/api/me")
	@Operation(summary = "Who am I: username and effective roles")
	public MeResponse me(Authentication authentication) {
		List<String> roles = roleHierarchy
				.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a.startsWith("ROLE_"))
				.map(a -> a.substring("ROLE_".length()))
				.distinct()
				.sorted()
				.toList();
		return new MeResponse(authentication.getName(), roles);
	}
}
