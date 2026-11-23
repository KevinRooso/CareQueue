package com.carequeue.performance;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.TestcontainersConfiguration;
import com.carequeue.imports.service.DeadLetterService;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import com.carequeue.referral.service.ReferralQueryService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Before/after evidence for the PRD performance requirement: the same waiting
 * list read at 25 and 200 rows must run a constant number of statements
 * (fetch join / entity graph), while a naive lazy-touching query grows
 * linearly — proving what the fix actually prevented.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class QueryCountTest {

	private static final Logger log = LoggerFactory.getLogger(QueryCountTest.class);

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private ReferralQueryService referralQueryService;

	@Autowired
	private DeadLetterService deadLetterService;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate tx(boolean readOnly) {
		TransactionTemplate t = new TransactionTemplate(transactionManager);
		t.setReadOnly(readOnly);
		return t;
	}

	@Test
	void waitingListQueries_scaleConstantly_naiveScalesLinearly() {
		long[] small = measure(25);
		long[] large = measure(200);

		log.info("statement counts N=25 : naive={} waitingList={} search={} deadLetters={}",
				small[0], small[1], small[2], small[3]);
		log.info("statement counts N=200: naive={} waitingList={} search={} deadLetters={}",
				large[0], large[1], large[2], large[3]);

		// (a) the naive query really does N+1: one per row per lazy association
		assertThat(small[0]).isGreaterThan(25);
		assertThat(large[0]).isGreaterThan(200);

		// (b), (c), (d) constant between the two sizes and at most 2 statements
		assertThat(small[1]).isEqualTo(large[1]).isLessThanOrEqualTo(2);
		assertThat(small[2]).isEqualTo(large[2]).isLessThanOrEqualTo(2);
		assertThat(small[3]).isEqualTo(large[3]).isLessThanOrEqualTo(2);
	}

	/** @return statement counts for [naive, waitingList, search, deadLetters] at size n. */
	private long[] measure(int n) {
		Statistics stats = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactory.class).getStatistics();

		Long clinicId = tx(false).execute(status -> {
			Clinic clinic = clinicRepository.save(new Clinic("PERF-" + n, "Perf " + n, "TESTING"));
			for (int i = 0; i < n; i++) {
				Patient p = patientRepository.save(new Patient(
						"MRN-P" + n + "-" + i, "Pat", "Test", LocalDate.of(1980, 1, 1)));
				Referral r = Referral.receive(p, clinic, "Dr S", "EXT-P" + n + "-" + i,
						ReferralSource.JSON, "notes", null, Instant.parse("2026-01-10T09:00:00Z"));
				r.recordSuggestion("s", Priority.ROUTINE, List.of(), null, "rules-v1",
						SuggestionSource.RULE_BASED_FALLBACK);
				r.confirmPriority(Priority.URGENT, null, "system", Instant.now());
				referralRepository.save(r);
			}
			return clinic.getId();
		});

		long[] counts = new long[4];

		// (a) naive JPQL, then touch the two lazy associations on every row
		stats.clear();
		tx(true).executeWithoutResult(status -> {
			List<Referral> rows = entityManager.createQuery(
					"select r from Referral r where r.clinic.id = :c and r.status = :st"
							+ " order by r.priorityRank, r.receivedAt", Referral.class)
					.setParameter("c", clinicId)
					.setParameter("st", ReferralStatus.TRIAGED)
					.getResultList();
			for (Referral r : rows) {
				r.getPatient().getLastName();
				r.getClinic().getName();
			}
		});
		counts[0] = stats.getPrepareStatementCount();

		// (b) the real waiting-list query (fetch join)
		stats.clear();
		tx(true).executeWithoutResult(status -> referralRepository
				.findWaitingList(clinicId, PageRequest.of(0, 500)));
		counts[1] = stats.getPrepareStatementCount();

		// (c) the filtered list endpoint (entity graph on patient + clinic)
		stats.clear();
		tx(true).executeWithoutResult(status -> referralQueryService
				.search(null, null, clinicId, PageRequest.of(0, 500)));
		counts[2] = stats.getPrepareStatementCount();

		// (d) dead-letter paging — different table, still constant
		stats.clear();
		tx(true).executeWithoutResult(status -> deadLetterService.page(PageRequest.of(0, 20)));
		counts[3] = stats.getPrepareStatementCount();

		return counts;
	}
}
