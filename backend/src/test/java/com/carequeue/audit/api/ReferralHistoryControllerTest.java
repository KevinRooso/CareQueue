package com.carequeue.audit.api;

import com.carequeue.audit.api.ReferralRevisionDto.FieldChange;
import com.carequeue.audit.service.ReferralHistoryService;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReferralHistoryController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class ReferralHistoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReferralHistoryService historyService;

	@Test
	void history_whenCalled_returnsRevisions() throws Exception {
		when(historyService.history(7L)).thenReturn(List.of(
				new ReferralRevisionDto(41L, Instant.parse("2026-02-01T10:00:00Z"), "nurse",
						"UPDATED", List.of(new FieldChange("status", "RECEIVED", "AI_SUGGESTED")))));

		mockMvc.perform(get("/api/referrals/7/history")
				.with(httpBasic("manager", "test-manager-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].revision").value(41))
				.andExpect(jsonPath("$[0].username").value("nurse"))
				.andExpect(jsonPath("$[0].type").value("UPDATED"))
				.andExpect(jsonPath("$[0].changes[0].field").value("status"))
				.andExpect(jsonPath("$[0].changes[0].from").value("RECEIVED"))
				.andExpect(jsonPath("$[0].changes[0].to").value("AI_SUGGESTED"));
	}

	@Test
	void history_whenReferralMissing_returns404() throws Exception {
		when(historyService.history(99L)).thenThrow(new NotFoundException("Referral 99 not found"));

		mockMvc.perform(get("/api/referrals/99/history")
				.with(httpBasic("manager", "test-manager-password")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void history_whenNoAuth_returns401() throws Exception {
		mockMvc.perform(get("/api/referrals/7/history"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void history_whenNurse_returns403() throws Exception {
		mockMvc.perform(get("/api/referrals/7/history")
				.with(httpBasic("nurse", "test-nurse-password")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}
}
