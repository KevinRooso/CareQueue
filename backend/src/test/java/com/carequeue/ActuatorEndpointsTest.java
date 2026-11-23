package com.carequeue;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.repository.ReferralRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actuator contract: health is public and minimal, probes are public, info and
 * metrics are ADMIN-only, and the triage counter actually increments.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ActuatorEndpointsTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void health_whenAnonymous_showsOnlyStatus() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void probes_whenAnonymous_arePublic() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void info_whenAnonymous_returns401() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void info_whenNurse_returns403() throws Exception {
		mockMvc.perform(get("/actuator/info").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isForbidden());
	}

	@Test
	void info_whenManager_showsBuildAndApp() throws Exception {
		mockMvc.perform(get("/actuator/info").with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.app.name").value("CareQueue"))
				.andExpect(jsonPath("$.build.artifact").value("carequeue"));
	}

	@Test
	void triageMetrics_afterSuggestion_countIncrements() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		Long id = new TransactionTemplate(transactionManager).execute(status -> {
			Clinic clinic = clinicRepository.save(new Clinic("ACT-" + unique, "Act Clinic", "GEN"));
			Patient patient = patientRepository.save(
					new Patient("MRN-A-" + unique, "Pat", "Test", LocalDate.of(1985, 5, 5)));
			return referralRepository.save(Referral.receive(patient, clinic, "Dr. Test",
					"EXT-" + unique, ReferralSource.JSON, "notes", "{}",
					Instant.now())).getId();
		});

		mockMvc.perform(post("/api/referrals/{id}/triage-suggestion", id)
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/actuator/metrics/carequeue.triage.suggestions")
				.with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.measurements[0].value", greaterThan(0.0)));
	}
}
