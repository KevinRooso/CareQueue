package com.carequeue.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CurrentUserController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class CurrentUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void me_whenNurse_returnsClinicianRole() throws Exception {
		mockMvc.perform(get("/api/me").with(httpBasic("nurse", "test-nurse-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("nurse"))
				.andExpect(jsonPath("$.roles").value(containsInAnyOrder("CLINICIAN")));
	}

	@Test
	void me_whenManager_returnsEffectiveRolesThroughHierarchy() throws Exception {
		// ADMIN implies CLINICIAN via the RoleHierarchy — this is the hierarchy proof.
		mockMvc.perform(get("/api/me").with(httpBasic("manager", "test-manager-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("manager"))
				.andExpect(jsonPath("$.roles").value(containsInAnyOrder("ADMIN", "CLINICIAN")));
	}

	@Test
	void me_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
