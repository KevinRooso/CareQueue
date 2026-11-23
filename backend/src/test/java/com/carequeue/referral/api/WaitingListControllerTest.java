package com.carequeue.referral.api;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.security.SecurityConfig;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.service.ReferralQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WaitingListController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class WaitingListControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReferralQueryService queryService;

	private static Referral triagedReferral() {
		Patient p = new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12));
		Clinic c = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
		ReflectionTestUtils.setField(p, "id", 1L);
		ReflectionTestUtils.setField(c, "id", 1L);
		Referral r = Referral.receive(p, c, "Dr Synthetic", "EXT-1", ReferralSource.JSON,
				"synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z"));
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.confirmPriority(Priority.URGENT, "n", "nurse", Instant.parse("2026-01-11T09:00:00Z"));
		ReflectionTestUtils.setField(r, "id", 42L);
		return r;
	}

	@Test
	void list_whenCalled_returnsWaitingListEntries() throws Exception {
		when(queryService.waitingList(eq(1L), any()))
				.thenReturn(new PageImpl<>(List.of(triagedReferral()), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/waiting-list?clinicId=1").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].confirmedPriority").value("URGENT"))
				.andExpect(jsonPath("$.content[0].patient.mrn").value("MRN-100001"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void list_whenClientSendsSort_ignoresIt() throws Exception {
		when(queryService.waitingList(eq(1L), any()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mockMvc.perform(get("/api/waiting-list?clinicId=1&sort=status,desc")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk());

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(queryService).waitingList(eq(1L), captor.capture());
		assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
	}

	@Test
	void list_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/waiting-list?clinicId=1"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
