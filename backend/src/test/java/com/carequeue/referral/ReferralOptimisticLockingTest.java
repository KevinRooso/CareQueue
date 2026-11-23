package com.carequeue.referral;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves optimistic locking through the Spring Data repository path — the exact
 * path a service will take, where Spring translates the stale-write failure into
 * {@link ObjectOptimisticLockingFailureException} (mapped to a 409 in M2).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ReferralOptimisticLockingTest {

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	@Test
	void save_whenAnotherTransactionChangedTheRow_throwsOptimisticLock() {
		// tx1: create the referral.
		Long id = tx().execute(status -> {
			Patient p = patientRepository.save(
					new Patient("MRN-LOCK001", "Lock", "Test", LocalDate.of(1985, 3, 3)));
			Clinic c = clinicRepository.save(new Clinic("LOCKTEST", "Locking Test Clinic", "TESTING"));
			return referralRepository.save(Referral.receive(p, c, "Dr Synthetic", "LOCK-1",
					ReferralSource.JSON, "synthetic notes", null,
					Instant.parse("2026-01-10T09:00:00Z"))).getId();
		});

		// tx2 and tx3: two copies, both detached once their transactions end.
		Referral a = tx().execute(status -> referralRepository.findById(id).orElseThrow());
		Referral b = tx().execute(status -> referralRepository.findById(id).orElseThrow());

		// tx4: copy A wins.
		a.recordSuggestion("summary from A", Priority.URGENT, List.of(), null,
				"triage-v1", SuggestionSource.AI);
		tx().executeWithoutResult(status -> referralRepository.save(a));

		// tx5: copy B is stale — version on the row no longer matches.
		b.recordSuggestion("summary from B", Priority.SOON, List.of(), null,
				"triage-v1", SuggestionSource.AI);
		assertThatThrownBy(() -> tx().executeWithoutResult(status -> referralRepository.save(b)))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}
}
