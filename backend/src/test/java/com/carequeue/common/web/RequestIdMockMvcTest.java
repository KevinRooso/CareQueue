package com.carequeue.common.web;

import com.carequeue.common.security.CurrentUserController;
import com.carequeue.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The X-Request-Id correlation header must appear on every kind of response:
 * success, security rejection and error — and the problem body must carry the
 * same id, so a caller can quote it back to support.
 */
@WebMvcTest(controllers = CurrentUserController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password",
		"spring.mvc.problemdetails.enabled=true"})
@Import(SecurityConfig.class)
class RequestIdMockMvcTest {

	private static final String HEADER = "X-Request-Id";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void request_whenOk_echoesValidId() throws Exception {
		mockMvc.perform(get("/api/me").with(httpBasic("nurse", "test-nurse-password"))
				.header(HEADER, "smoke-123"))
				.andExpect(status().isOk())
				.andExpect(header().string(HEADER, "smoke-123"));
	}

	@Test
	void request_whenUnauthenticated_401CarriesId() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/me").header(HEADER, "smoke-401"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HEADER, "smoke-401"))
				.andExpect(jsonPath("$.requestId").value("smoke-401"))
				.andReturn();
		assertThat(result.getResponse().getHeader(HEADER)).isEqualTo("smoke-401");
	}

	@Test
	void request_whenNotFound_problemAndHeaderShareTheId() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/does-not-exist")
				.with(httpBasic("nurse", "test-nurse-password")))
				.andExpect(status().isNotFound())
				.andExpect(header().exists(HEADER))
				.andReturn();

		String headerValue = result.getResponse().getHeader(HEADER);
		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"requestId\":\"" + headerValue + "\"");
	}

	@Test
	void request_whenInvalidIncomingId_replacedWithGenerated() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/me")
				.with(httpBasic("nurse", "test-nurse-password"))
				.header(HEADER, "bad id with spaces"))
				.andExpect(status().isOk())
				.andReturn();

		String id = result.getResponse().getHeader(HEADER);
		assertThat(id).isNotEqualTo("bad id with spaces").isNotBlank();
	}
}
