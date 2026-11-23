package com.carequeue.appointment.api;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.service.AppointmentService;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.security.SecurityConfig;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = AppointmentController.class, properties = {
		"carequeue.security.nurse-password=test-nurse-password",
		"carequeue.security.manager-password=test-manager-password"})
@Import(SecurityConfig.class)
class AppointmentControllerTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";
	private static final Instant STARTS = Instant.parse("2030-03-01T09:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

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
	void book_whenManager_returns201() throws Exception {
		Referral r = triagedReferral();
		Appointment a = Appointment.bookFor(r, STARTS);
		ReflectionTestUtils.setField(a, "id", 7L);
		when(appointmentService.book(42L, STARTS)).thenReturn(a);

		mockMvc.perform(post("/api/referrals/42/appointment")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content("{\"startsAt\":\"2030-03-01T09:00:00Z\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.referralId").value(42))
				.andExpect(jsonPath("$.status").value("BOOKED"))
				.andExpect(jsonPath("$.startsAt").value("2030-03-01T09:00:00Z"));
	}

	@Test
	void book_whenNurse_returns403() throws Exception {
		// ADMIN-only endpoint; CLINICIAN is denied — and the hierarchy does not leak downward.
		mockMvc.perform(post("/api/referrals/42/appointment")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"startsAt\":\"2030-03-01T09:00:00Z\"}"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Forbidden"));
	}

	@Test
	void book_whenReferralNotTriaged_returns409() throws Exception {
		when(appointmentService.book(42L, STARTS))
				.thenThrow(new InvalidStatusTransitionException(
						ReferralStatus.RECEIVED, ReferralStatus.BOOKED));

		mockMvc.perform(post("/api/referrals/42/appointment")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content("{\"startsAt\":\"2030-03-01T09:00:00Z\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Invalid status transition"));
	}

	@Test
	void book_whenStartsAtInPast_returns400() throws Exception {
		mockMvc.perform(post("/api/referrals/42/appointment")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content("{\"startsAt\":\"2020-01-01T09:00:00Z\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("startsAt"));
	}

	@Test
	void book_whenUnauthenticated_returns401WithoutChallenge() throws Exception {
		mockMvc.perform(post("/api/referrals/42/appointment")
				.contentType("application/json")
				.content("{\"startsAt\":\"2030-03-01T09:00:00Z\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}
}
