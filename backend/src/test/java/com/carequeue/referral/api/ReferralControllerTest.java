package com.carequeue.referral.api;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.error.BadSortException;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.common.security.SecurityConfig;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.service.ReferralCommandService;
import com.carequeue.referral.service.ReferralQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReferralController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class ReferralControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReferralQueryService queryService;

	@MockitoBean
	private ReferralCommandService commandService;

	private static Referral referral() {
		Patient p = new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12));
		Clinic c = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
		ReflectionTestUtils.setField(p, "id", 1L);
		ReflectionTestUtils.setField(c, "id", 1L);
		Referral r = Referral.receive(p, c, "Dr Synthetic", "EXT-1", ReferralSource.JSON,
				"synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z"));
		ReflectionTestUtils.setField(r, "id", 42L);
		return r;
	}

	@Test
	void list_whenCalled_returnsPagedSummaries() throws Exception {
		Referral r = referral();
		r.recordSuggestion("summary", Priority.URGENT, List.of("weight loss"),
				new BigDecimal("0.85"), "triage-v1", SuggestionSource.AI);
		when(queryService.search(any(), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/referrals").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(42))
				.andExpect(jsonPath("$.content[0].status").value("AI_SUGGESTED"))
				.andExpect(jsonPath("$.content[0].patient.mrn").value("MRN-100001"))
				.andExpect(jsonPath("$.content[0].clinic.code").value("GASTRO"))
				.andExpect(jsonPath("$.content[0].suggestedPriority").value("URGENT"))
				.andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void list_withFilters_passesThemToService() throws Exception {
		when(queryService.search(any(), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mockMvc.perform(get("/api/referrals?status=TRIAGED&priority=URGENT&clinicId=2")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk());

		verify(queryService).search(eq(ReferralStatus.TRIAGED), eq(Priority.URGENT), eq(2L), any());
	}

	@Test
	void list_whenSortIsNotAllowed_returns400() throws Exception {
		when(queryService.search(any(), any(), any(), any()))
				.thenThrow(new BadSortException("clinicalNotes"));

		mockMvc.perform(get("/api/referrals?sort=clinicalNotes,desc")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid sort"));
	}

	@Test
	void detail_whenExists_returnsAllFields() throws Exception {
		Referral r = referral();
		r.recordSuggestion("summary", Priority.URGENT, List.of("weight loss"),
				new BigDecimal("0.85"), "triage-v1", SuggestionSource.AI);
		when(queryService.getById(42L)).thenReturn(r);

		mockMvc.perform(get("/api/referrals/42").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.referringGp").value("Dr Synthetic"))
				.andExpect(jsonPath("$.aiSummary").value("summary"))
				.andExpect(jsonPath("$.redFlags[0]").value("weight loss"))
				.andExpect(jsonPath("$.promptVersion").value("triage-v1"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void detail_whenNotFound_returns404Problem() throws Exception {
		when(queryService.getById(99L)).thenThrow(new NotFoundException("Referral 99 not found"));

		mockMvc.perform(get("/api/referrals/99").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void confirm_whenValid_returnsUpdatedDetail() throws Exception {
		Referral r = referral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.confirmPriority(Priority.URGENT, "note", NURSE, Instant.parse("2026-02-01T10:00:00Z"));
		when(commandService.confirm(eq(42L), eq(Priority.URGENT), eq("2ww"), eq(0L), eq(NURSE)))
				.thenReturn(r);

		mockMvc.perform(post("/api/referrals/42/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"URGENT\",\"note\":\"2ww\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TRIAGED"))
				.andExpect(jsonPath("$.confirmedPriority").value("URGENT"));
	}

	@Test
	void confirm_whenBodyInvalid_returns400WithErrorList() throws Exception {
		mockMvc.perform(post("/api/referrals/42/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"URGENT\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("version"));
	}

	@Test
	void confirm_whenVersionStale_returns409() throws Exception {
		when(commandService.confirm(anyLong(), any(), any(), eq(99L), anyString()))
				.thenThrow(new StaleVersionException(99L, 3L));

		mockMvc.perform(post("/api/referrals/42/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"URGENT\",\"note\":\"x\",\"version\":99}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Stale version"));
	}

	@Test
	void reject_whenValid_returnsUpdatedDetail() throws Exception {
		Referral r = referral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.reject();
		when(commandService.reject(42L, 0L)).thenReturn(r);

		mockMvc.perform(post("/api/referrals/42/reject")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));
	}

	@Test
	void postWithBasicAuth_whenNoCsrfToken_succeeds() throws Exception {
		Referral r = referral();
		r.reject();
		when(commandService.reject(42L, 0L)).thenReturn(r);

		// No CSRF token at all — must still work for /api/** (ADR-009).
		mockMvc.perform(post("/api/referrals/42/reject")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"version\":0}"))
				.andExpect(status().isOk());
	}

	@Test
	void list_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/referrals"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
