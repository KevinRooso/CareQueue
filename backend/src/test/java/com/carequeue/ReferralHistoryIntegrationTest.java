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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-9 end to end: import-equivalent create, AI suggestion, confirm and booking
 * as different users, then the history shows each change with the right actor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ReferralHistoryIntegrationTest {

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
	private JsonMapper jsonMapper;

	@Test
	void history_afterFullFlow_showsEveryActorAndChange() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		Long id = new TransactionTemplate(transactionManager).execute(status -> {
			Clinic clinic = clinicRepository.save(new Clinic("HIST-" + unique, "History Clinic", "GEN"));
			Patient patient = patientRepository.save(
					new Patient("MRN-H-" + unique, "Pat", "Test", LocalDate.of(1985, 5, 5)));
			return referralRepository.save(Referral.receive(patient, clinic, "Dr. Test",
					"EXT-" + unique, ReferralSource.JSON, "notes", "{}",
					Instant.now())).getId();
		});

		MvcResult suggestion = mockMvc.perform(
				post("/api/referrals/{id}/triage-suggestion", id).with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andReturn();
		long version = jsonMapper.readTree(suggestion.getResponse().getContentAsString())
				.get("version").longValue();

		mockMvc.perform(post("/api/referrals/{id}/triage", id).with(httpBasic(NURSE, NURSE_PW))
				.contentType(APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						Map.of("priority", "URGENT", "note", "escalating", "version", version))))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/referrals/{id}/appointment", id)
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType(APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("startsAt", "2030-06-01T09:00:00Z"))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/referrals/{id}/history", id).with(httpBasic(MANAGER, MANAGER_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].type").value("CREATED"))
				.andExpect(jsonPath("$[0].username").value("system"))
				.andExpect(jsonPath("$[0].changes[?(@.field=='status')].to", hasItem("RECEIVED")))
				.andExpect(jsonPath("$[1].type").value("UPDATED"))
				.andExpect(jsonPath("$[1].username").value(NURSE))
				.andExpect(jsonPath("$[1].changes[?(@.field=='status')].to", hasItem("AI_SUGGESTED")))
				.andExpect(jsonPath("$[1].changes[?(@.field=='suggestionSource')].to",
						hasItem("RULE_BASED_FALLBACK")))
				.andExpect(jsonPath("$[2].type").value("UPDATED"))
				.andExpect(jsonPath("$[2].username").value(NURSE))
				.andExpect(jsonPath("$[2].changes[?(@.field=='status')].to", hasItem("TRIAGED")))
				.andExpect(jsonPath("$[2].changes[?(@.field=='confirmedPriority')].to",
						hasItem("URGENT")))
				.andExpect(jsonPath("$[2].changes[?(@.field=='triagedBy')].to", hasItem(NURSE)))
				.andExpect(jsonPath("$[3].type").value("UPDATED"))
				.andExpect(jsonPath("$[3].username").value(MANAGER))
				.andExpect(jsonPath("$[3].changes[?(@.field=='status')].to", hasItem("BOOKED")));
	}
}
