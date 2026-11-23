package com.carequeue;

import com.carequeue.imports.repository.DeadLetterMessageRepository;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "GROQ_API_KEY=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ImportIntegrationTest {

	private static final String NURSE = "nurse";
	private static final String NURSE_PW = "test-nurse-password";
	private static final String MANAGER = "manager";
	private static final String MANAGER_PW = "test-manager-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private DeadLetterMessageRepository deadLetterRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	private static String sample(String name) throws Exception {
		return Files.readString(Path.of("../samples/" + name));
	}

	@Test
	void importJsonXmlAndDuplicate_endToEnd() throws Exception {
		String json = sample("referral-sample.json");

		// JSON → 201, stored with source JSON and the original text as raw_message.
		String location = mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json").content(json))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
						"/api/referrals/\\d+")))
				.andExpect(jsonPath("$.created").value(true))
				.andReturn().getResponse().getHeader("Location");

		long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		tx().executeWithoutResult(status -> {
			var r = referralRepository.findById(id).orElseThrow();
			assertThat(r.getSource().name()).isEqualTo("JSON");
			// raw_message is JSONB — Postgres normalizes key order, so compare trees.
			assertThat(JsonMapper.shared().readTree(r.getRawMessage()))
					.isEqualTo(JsonMapper.shared().readTree(json));
			assertThat(r.getStatus().name()).isEqualTo("RECEIVED");
		});

		// Same JSON again → 200, same id, no new row.
		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json").content(json))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.created").value(false));

		// XML → 201, source XML, raw_message is the JSON-wrapped original.
		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/xml").content(sample("referral-sample.xml")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.created").value(true));
		tx().executeWithoutResult(status -> {
			var r = referralRepository.findByExternalRef("GP-2026-000482").orElseThrow();
			assertThat(r.getSource().name()).isEqualTo("XML");
			assertThat(r.getRawMessage()).contains("\"xml\":");
			assertThat(r.getRawMessage()).contains("referral");
		});

		// The imported JSON referral is listed as RECEIVED.
		mockMvc.perform(get("/api/referrals?externalRef").with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/referrals/" + id).with(httpBasic(NURSE, NURSE_PW)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RECEIVED"));
	}

	@Test
	void importInvalidXml_returns422AndCommitsDeadLetter() throws Exception {
		long before = tx().execute(s -> deadLetterRepository.count()).longValue();

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/xml").content(sample("referral-invalid.xml")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.title").value("Invalid referral message"))
				.andExpect(jsonPath("$.deadLetterId").isNumber());

		assertThat(tx().execute(s -> deadLetterRepository.count()).longValue())
				.isEqualTo(before + 1);
	}

	@Test
	void importUnknownClinic_returns422WithDeadLetter() throws Exception {
		String unknown = sample("referral-sample.json").replace("GASTRO", "NOPE");

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/json").content(unknown))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.detail").value("Unknown clinic code."))
				.andExpect(jsonPath("$.deadLetterId").isNumber());
	}

	@Test
	void importXxe_returns422() throws Exception {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE referral [
				  <!ENTITY xxe SYSTEM "file:///etc/passwd">
				]>
				<referral externalRef="GP-XXE-1">
				  <referringGp>&xxe;</referringGp>
				  <clinicCode>GASTRO</clinicCode>
				  <patient mrn="MRN-XXE-1">
				    <firstName>A</firstName><lastName>B</lastName><dateOfBirth>1990-01-01</dateOfBirth>
				  </patient>
				  <clinicalNotes>notes</clinicalNotes>
				</referral>""";

		mockMvc.perform(post("/api/referrals/import")
				.with(httpBasic(MANAGER, MANAGER_PW))
				.contentType("application/xml").content(xxe))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.deadLetterId").isNumber());
	}
}
