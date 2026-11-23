package com.carequeue;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ApiIntegrationTest {

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

	@Autowired
	private EntityManager entityManager;

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	private long newSuggestedReferral(String suffix, String clinicCode) {
		return tx().execute(status -> {
			Patient p = patientRepository.save(new Patient(
					"MRN-" + suffix, "Pat", "Test", LocalDate.of(1980, 1, 1)));
			Clinic c = clinicRepository.findByCode(clinicCode).orElseThrow();
			Referral r = Referral.receive(p, c, "Dr Synthetic", "EXT-" + suffix,
					ReferralSource.JSON, "synthetic notes", null,
					Instant.parse("2026-01-10T09:00:00Z"));
			r.recordSuggestion("summary", Priority.SOON, List.of(),
					new BigDecimal("0.60"), "triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
			return referralRepository.save(r).getId();
		});
	}

	@Test
	void e2e_confirmBookAndWaitingList_flowWorks() throws Exception {
		long id = newSuggestedReferral("API-1", "GASTRO");

		// Nurse confirms at version 0 → TRIAGED.
		mockMvc.perform(post("/api/referrals/" + id + "/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"URGENT\",\"note\":\"2ww\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TRIAGED"))
				.andExpect(jsonPath("$.confirmedPriority").value("URGENT"))
				.andExpect(jsonPath("$.version").value(1));

		// The waiting list now shows it.
		mockMvc.perform(get("/api/waiting-list?clinicId="
				+ tx().execute(s -> entityManager.find(Referral.class, id).getClinic().getId()))
				.with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

		// Manager books it → 201 and status BOOKED.
		mockMvc.perform(post("/api/referrals/" + id + "/appointment")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json")
				.content("{\"startsAt\":\"2030-03-01T09:00:00Z\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("BOOKED"));

		// Re-confirming with the stale version → 409 problem+json.
		mockMvc.perform(post("/api/referrals/" + id + "/triage")
				.with(httpBasic(NURSE, NURSE_PW))
				.contentType("application/json")
				.content("{\"priority\":\"SOON\",\"note\":\"x\",\"version\":0}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentType("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Stale version"));
	}

	@Test
	void referrals_whenNVaries_statementCountIsConstant() {
		Statistics stats = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactory.class).getStatistics();

		long count = countListStatements(stats, 5);
		long count25 = countListStatements(stats, 25);

		// > 0 proves statistics are actually on — a 0==0 comparison was vacuous.
		assertThat(count).isPositive().isEqualTo(count25);
		assertThat(count).isLessThanOrEqualTo(2);
	}

	private long countListStatements(Statistics stats, int n) {
		tx().executeWithoutResult(status -> {
			Clinic c = clinicRepository.save(new Clinic("LST" + n, "List Clinic " + n, "TESTING"));
			for (int i = 0; i < n; i++) {
				Patient p = patientRepository.save(new Patient(
						"MRN-L" + n + "-" + i, "L", "Test", LocalDate.of(1980, 1, 1)));
				referralRepository.save(Referral.receive(p, c, "Dr S", "EXT-L" + n + "-" + i,
						ReferralSource.JSON, "notes", null, Instant.parse("2026-01-10T09:00:00Z")));
			}
		});
		stats.clear();
		try {
			mockMvc.perform(get("/api/referrals?size=100")
					.with(httpBasic(NURSE, NURSE_PW)))
					.andExpect(status().isOk());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return stats.getPrepareStatementCount();
	}

	@Test
	void apiDocs_whenUnauthenticated_returnsSpec() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/api/referrals")));
	}

	@Test
	void swaggerUi_whenUnauthenticated_isReachable() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(result -> assertThat(result.getResponse().getStatus())
						.isIn(200, 301, 302));
	}

	@Test
	void actuator_healthPublic_metricsProtected() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("WWW-Authenticate"));

		mockMvc.perform(get("/actuator/metrics").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/actuator/metrics").with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk());
	}
}
