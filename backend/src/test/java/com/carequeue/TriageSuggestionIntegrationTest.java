package com.carequeue;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class TriageSuggestionIntegrationTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";

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

	@Autowired
	private EntityManager entityManager;

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	@Test
	void suggestThenConfirm_receivedReferralBecomesTriaged() throws Exception {
		long id = tx().execute(status -> {
			Patient p = patientRepository.save(new Patient(
					"MRN-TRI-1", "Pat", "Test", LocalDate.of(1980, 1, 1)));
			Clinic c = clinicRepository.save(new Clinic("TRI", "Triage Test Clinic", "TESTING"));
			return referralRepository.save(Referral.receive(p, c, "Dr Synthetic", "EXT-TRI-1",
					ReferralSource.JSON, "synthetic notes for triage",
					null, Instant.parse("2026-01-10T09:00:00Z"))).getId();
		});

		// Suggest (no GROQ key in tests → rule-based fallback).
		MvcResult result = mockMvc.perform(post("/api/referrals/" + id + "/triage-suggestion")
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AI_SUGGESTED"))
				.andExpect(jsonPath("$.suggestionSource").value("RULE_BASED_FALLBACK"))
				.andExpect(jsonPath("$.promptVersion").value("rules-v1"))
				.andExpect(jsonPath("$.confirmedPriority").doesNotExist())
				.andExpect(jsonPath("$.version").value(1))
				.andReturn();

		// An Envers revision was written for the suggestion.
		Number audRows = (Number) tx().execute(status -> entityManager
				.createNativeQuery("select count(*) from referral_aud where id = :id")
				.setParameter("id", id).getSingleResult());
		assertThat(audRows.longValue()).isGreaterThanOrEqualTo(2); // insert + suggestion

		// Confirming with the returned version succeeds.
		mockMvc.perform(post("/api/referrals/" + id + "/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"URGENT\",\"note\":\"confirmed\",\"version\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TRIAGED"))
				.andExpect(jsonPath("$.confirmedPriority").value("URGENT"));
	}
}
