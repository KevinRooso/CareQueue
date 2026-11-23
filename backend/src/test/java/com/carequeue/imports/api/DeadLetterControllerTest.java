package com.carequeue.imports.api;

import com.carequeue.common.security.SecurityConfig;
import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.service.DeadLetterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeadLetterController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class DeadLetterControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeadLetterService deadLetterService;

	@Test
	void list_whenManager_returnsDeadLetters() throws Exception {
		DeadLetterMessage m = new DeadLetterMessage(ImportFormat.JSON,
				"{\"broken\": true}", "invalid json", Instant.parse("2026-01-10T09:00:00Z"));
		ReflectionTestUtils.setField(m, "id", 5L);
		when(deadLetterService.page(any()))
				.thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/dead-letters").with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(5))
				.andExpect(jsonPath("$.content[0].source").value("JSON"))
				.andExpect(jsonPath("$.content[0].error").value("invalid json"));
	}

	@Test
	void list_whenNurse_returns403() throws Exception {
		mockMvc.perform(get("/api/dead-letters").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	@Test
	void list_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/dead-letters"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
