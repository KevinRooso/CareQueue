package com.carequeue.triage.api;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.security.SecurityConfig;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.service.TriageSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TriageSuggestionController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class TriageSuggestionControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TriageSuggestionService triageSuggestionService;

	private static Referral suggestedReferral() {
		Patient p = new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12));
		Clinic c = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
		ReflectionTestUtils.setField(p, "id", 1L);
		ReflectionTestUtils.setField(c, "id", 1L);
		Referral r = Referral.receive(p, c, "Dr Synthetic", "EXT-1", ReferralSource.JSON,
				"synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z"));
		ReflectionTestUtils.setField(r, "id", 42L);
		r.recordSuggestion("short summary", Priority.URGENT, List.of("weight loss"),
				new BigDecimal("0.87"), "rules-v1", SuggestionSource.RULE_BASED_FALLBACK);
		return r;
	}

	@Test
	void suggest_whenCalled_returnsDetailWithSuggestionFields() throws Exception {
		when(triageSuggestionService.suggest(42L)).thenReturn(suggestedReferral());

		mockMvc.perform(post("/api/referrals/42/triage-suggestion")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.status").value("AI_SUGGESTED"))
				.andExpect(jsonPath("$.suggestedPriority").value("URGENT"))
				.andExpect(jsonPath("$.suggestionSource").value("RULE_BASED_FALLBACK"))
				.andExpect(jsonPath("$.promptVersion").value("rules-v1"))
				.andExpect(jsonPath("$.confirmedPriority").doesNotExist());
	}

	@Test
	void suggest_whenManager_isAllowedThroughHierarchy() throws Exception {
		when(triageSuggestionService.suggest(42L)).thenReturn(suggestedReferral());

		mockMvc.perform(post("/api/referrals/42/triage-suggestion")
				.with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk());
	}

	@Test
	void suggest_whenNotFound_returns404() throws Exception {
		when(triageSuggestionService.suggest(99L))
				.thenThrow(new NotFoundException("Referral 99 not found"));

		mockMvc.perform(post("/api/referrals/99/triage-suggestion")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void suggest_whenStatusCannotSuggest_returns409() throws Exception {
		when(triageSuggestionService.suggest(42L))
				.thenThrow(new InvalidStatusTransitionException(
						ReferralStatus.TRIAGED, ReferralStatus.AI_SUGGESTED));

		mockMvc.perform(post("/api/referrals/42/triage-suggestion")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Invalid status transition"));
	}

	@Test
	void suggest_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(post("/api/referrals/42/triage-suggestion"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
