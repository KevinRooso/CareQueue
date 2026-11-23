package com.carequeue.common.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401 as problem+json — deliberately WITHOUT a WWW-Authenticate header, so the
 * browser never shows its native login prompt and never caches Basic credentials
 * (that's what makes CSRF-off safe for /api/** — see ADR-009).
 */
public class ProblemJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ProblemJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Authentication is required for this endpoint.");
		problem.setTitle("Unauthorized");
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), problem);
	}
}
