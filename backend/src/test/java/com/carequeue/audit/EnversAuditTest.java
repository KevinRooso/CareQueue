package com.carequeue.audit;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.audit.domain.AuditRevision;
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
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class EnversAuditTest {

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@PersistenceContext
	private EntityManager entityManager;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	@Test
	void changes_whenCommitted_areAuditedWithUsername() {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("nurse.test", "pw", "ROLE_CLINICIAN"));

		// Revision 1: the referral is created.
		Long referralId = tx().execute(status -> {
			Patient p = patientRepository.save(
					new Patient("MRN-800001", "Kai", "Test", LocalDate.of(1977, 4, 2)));
			Clinic c = clinicRepository.save(new Clinic("ENVTEST", "Envers Test Clinic", "TESTING"));
			return referralRepository.save(Referral.receive(p, c, "Dr Synthetic", "ENV-1",
					ReferralSource.JSON, "synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z")))
					.getId();
		});

		// Revision 2: a later transaction suggests and confirms.
		tx().executeWithoutResult(status -> {
			Referral r = referralRepository.findById(referralId).orElseThrow();
			r.recordSuggestion("summary", Priority.URGENT, List.of("weight loss"),
					new BigDecimal("0.85"), "triage-v1", SuggestionSource.AI);
			r.confirmPriority(Priority.URGENT, "synthetic note", "nurse.test",
					Instant.parse("2026-01-11T09:00:00Z"));
			referralRepository.save(r);
		});

		List<Number> revisions = tx().execute(status -> {
			AuditReader reader = AuditReaderFactory.get(entityManager);
			return reader.getRevisions(Referral.class, referralId);
		});

		assertThat(revisions).hasSize(2);
		for (Number rev : revisions) {
			AuditRevision auditRevision = tx().execute(status ->
					AuditReaderFactory.get(entityManager)
							.findRevision(AuditRevision.class, rev));
			assertThat(auditRevision.getUsername()).isEqualTo("nurse.test");
			assertThat(auditRevision.getTimestamp()).isNotNull();
		}

		// A transaction with no authentication records "system".
		SecurityContextHolder.clearContext();
		tx().executeWithoutResult(status -> {
			Patient p = patientRepository.save(
					new Patient("MRN-800002", "Noa", "Test", LocalDate.of(1992, 9, 21)));
			Clinic c = clinicRepository.save(new Clinic("ENVTEST2", "Envers Clinic Two", "TESTING"));
			referralRepository.save(Referral.receive(p, c, "Dr Synthetic", "ENV-2",
					ReferralSource.JSON, "synthetic notes", null, Instant.parse("2026-01-12T09:00:00Z")));
		});

		AuditRevision latest = tx().execute(status -> entityManager
				.createQuery("select r from AuditRevision r order by r.id desc", AuditRevision.class)
				.setMaxResults(1)
				.getSingleResult());
		assertThat(latest.getUsername()).isEqualTo("system");
	}
}
