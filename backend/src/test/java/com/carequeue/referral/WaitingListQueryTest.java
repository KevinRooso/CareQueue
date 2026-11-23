package com.carequeue.referral;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.appointment.domain.Appointment;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
		"spring.jpa.show-sql=true",
		"spring.jpa.properties.hibernate.generate_statistics=true"})
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class WaitingListQueryTest {

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private EntityManager em;

	private Clinic clinic;
	private int mrnSeq;
	private int refSeq;

	@BeforeEach
	void setUp() {
		// Each test uses its own clinic so seeded/other data cannot leak in.
		clinic = clinicRepository.save(new Clinic("WLTEST", "Waiting List Test Clinic", "TESTING"));
	}

	private Referral referralAt(Clinic c, Instant receivedAt) {
		Patient p = patientRepository.save(new Patient(
				String.format("MRN-2%05d", ++mrnSeq), "Sam", "Test", LocalDate.of(1975, 3, 9)));
		Referral r = Referral.receive(p, c, "Dr Synthetic",
				String.format("WL-%04d", ++refSeq), ReferralSource.JSON, "synthetic notes", null, receivedAt);
		return referralRepository.save(r);
	}

	private void triage(Referral r, Priority priority) {
		r.recordSuggestion("s", Priority.SOON, List.of(), new BigDecimal("0.60"),
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
		r.confirmPriority(priority, "n", "nurse", Instant.parse("2026-02-01T09:00:00Z"));
	}

	@Test
	void findWaitingList_ordersByRankThenReceivedAt_andFiltersOthers() {
		Referral routine1 = referralAt(clinic, Instant.parse("2026-01-01T09:00:00Z"));
		Referral soon2 = referralAt(clinic, Instant.parse("2026-01-02T09:00:00Z"));
		Referral urgent3 = referralAt(clinic, Instant.parse("2026-01-03T09:00:00Z"));
		Referral urgent4 = referralAt(clinic, Instant.parse("2026-01-04T09:00:00Z"));
		Referral received = referralAt(clinic, Instant.parse("2026-01-05T09:00:00Z"));
		Referral booked = referralAt(clinic, Instant.parse("2026-01-06T09:00:00Z"));

		triage(routine1, Priority.ROUTINE);
		triage(soon2, Priority.SOON);
		triage(urgent3, Priority.URGENT);
		triage(urgent4, Priority.URGENT);
		triage(booked, Priority.SOON);
		entityManager.persist(Appointment.bookFor(booked, Instant.parse("2026-03-01T09:00:00Z")));

		// A TRIAGED referral in a different clinic must not appear.
		Clinic other = clinicRepository.save(new Clinic("OTHER", "Other Clinic", "TESTING"));
		Patient op = patientRepository.save(new Patient("MRN-299999", "Jo", "Test", LocalDate.of(1990, 1, 1)));
		Referral otherRef = Referral.receive(op, other, "Dr Synthetic", "WL-OTHER",
				ReferralSource.JSON, "notes", null, Instant.parse("2026-01-07T09:00:00Z"));
		referralRepository.save(otherRef);
		triage(otherRef, Priority.URGENT);

		entityManager.flush();
		entityManager.clear();

		Page<Referral> page = referralRepository.findWaitingList(clinic.getId(), PageRequest.of(0, 10));

		assertThat(page.getContent())
				.extracting(Referral::getExternalRef)
				.containsExactly("WL-0003", "WL-0004", "WL-0002", "WL-0001");
		assertThat(page.getContent())
				.allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReferralStatus.TRIAGED));
	}

	@Test
	void findWaitingList_whenPaged_returnsPageSliceAndTotal() {
		for (int i = 0; i < 4; i++) {
			Referral r = referralAt(clinic, Instant.parse("2026-01-0" + (i + 1) + "T09:00:00Z"));
			triage(r, Priority.URGENT);
		}
		entityManager.flush();
		entityManager.clear();

		Page<Referral> page = referralRepository.findWaitingList(clinic.getId(), PageRequest.of(0, 2));

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getTotalPages()).isEqualTo(2);
	}

	@Test
	void findWaitingList_whenTouchingAssociations_runsConstantQueries() {
		long for5 = countStatements(5);
		long for25 = countStatements(25);
		assertThat(for5).isEqualTo(for25);
		assertThat(for5).isLessThanOrEqualTo(2);
	}

	private long countStatements(int n) {
		Clinic c = clinicRepository.save(new Clinic("NPLUS" + n, "N+1 Clinic " + n, "TESTING"));
		for (int i = 0; i < n; i++) {
			Referral r = referralAt(c, Instant.parse("2026-01-10T09:00:00Z").plusSeconds(i * 60));
			triage(r, Priority.SOON);
		}
		entityManager.flush();
		entityManager.clear();

		Statistics stats = em.getEntityManagerFactory()
				.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		Page<Referral> page = referralRepository.findWaitingList(c.getId(), PageRequest.of(0, 100));
		for (Referral r : page.getContent()) {
			r.getPatient().getLastName();
			r.getClinic().getName();
		}

		return stats.getPrepareStatementCount();
	}
}
