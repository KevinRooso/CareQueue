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
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ReferralRepositoryTest {

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Patient patient;
	private Clinic clinic;

	@BeforeEach
	void setUp() {
		patient = patientRepository.save(new Patient(
				"MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12)));
		clinic = clinicRepository.save(new Clinic("TESTCLINIC", "Test Clinic", "TESTING"));
	}

	private Referral newReferral() {
		return Referral.receive(patient, clinic, "Dr Synthetic", "EXT-1001",
				ReferralSource.JSON, "synthetic clinical notes",
				"{\"externalRef\":\"EXT-1001\",\"format\":\"json\"}",
				Instant.parse("2026-01-10T09:00:00Z"));
	}

	@Test
	void save_whenReloaded_restoresAllFields() {
		Referral saved = referralRepository.save(newReferral());
		entityManager.flush();
		entityManager.clear();

		Referral found = referralRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getReferringGp()).isEqualTo("Dr Synthetic");
		assertThat(found.getExternalRef()).isEqualTo("EXT-1001");
		assertThat(found.getSource()).isEqualTo(ReferralSource.JSON);
		assertThat(found.getStatus()).isEqualTo(ReferralStatus.RECEIVED);
		assertThat(found.getReceivedAt()).isEqualTo(Instant.parse("2026-01-10T09:00:00Z"));
		assertThat(found.getVersion()).isZero();
		assertThat(found.getPatient().getMrn()).isEqualTo("MRN-100001");
		assertThat(found.getClinic().getCode()).isEqualTo("TESTCLINIC");
	}

	@Test
	void findByExternalRef_whenExists_returnsReferral() {
		referralRepository.save(newReferral());
		assertThat(referralRepository.findByExternalRef("EXT-1001")).isPresent();
		assertThat(referralRepository.findByExternalRef("EXT-9999")).isEmpty();
	}

	@Test
	void save_whenRedFlagsSet_roundTripsJsonb() {
		Referral r = newReferral();
		r.recordSuggestion("summary", Priority.URGENT, List.of("weight loss", "rectal bleeding"),
				new BigDecimal("0.85"), "triage-v1", SuggestionSource.AI);
		referralRepository.save(r);
		entityManager.flush();
		entityManager.clear();

		Referral found = referralRepository.findById(r.getId()).orElseThrow();
		assertThat(found.getRedFlags()).containsExactly("weight loss", "rectal bleeding");
		assertThat(found.getAiSummary()).isEqualTo("summary");
		assertThat(found.getSuggestionConfidence()).isEqualByComparingTo("0.85");
	}

	@Test
	void save_whenRawMessageJson_storedAsJsonObject() {
		Referral r = referralRepository.save(newReferral());
		entityManager.flush();
		entityManager.clear();

		Object value = entityManager.getEntityManager()
				.createNativeQuery("select raw_message->>'externalRef' from referral where id = ?1")
				.setParameter(1, r.getId())
				.getSingleResult();
		assertThat(value).isEqualTo("EXT-1001");
	}

	@Test
	void save_whenDuplicateExternalRef_fails() {
		referralRepository.save(newReferral());
		entityManager.flush();
		entityManager.clear();

		Referral dup = newReferral();
		assertThatThrownBy(() -> {
			referralRepository.save(dup);
			entityManager.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void insert_whenStatusNotInCheck_fails() {
		assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery("""
				insert into referral (patient_id, clinic_id, referring_gp, source, clinical_notes, status, received_at)
				values (?1, ?2, 'Dr X', 'JSON', 'notes', 'BOGUS', now())
				""")
				.setParameter(1, patient.getId())
				.setParameter(2, clinic.getId())
				.executeUpdate())
				.isInstanceOf(ConstraintViolationException.class)
				.hasMessageContaining("referral_status_check");
	}

	@Test
	void update_whenConfidenceOutOfRange_failsCheck() {
		Referral r = referralRepository.save(newReferral());
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
				"update referral set suggestion_confidence = 1.50 where id = ?1")
				.setParameter(1, r.getId())
				.executeUpdate())
				.isInstanceOf(ConstraintViolationException.class)
				.hasMessageContaining("referral_suggestion_confidence_check");
	}

	@Test
	void flush_whenRowChangedUnderneath_throwsOptimisticLock() {
		Referral r = referralRepository.save(newReferral());
		entityManager.flush();

		entityManager.getEntityManager().createNativeQuery(
				"update referral set version = version + 1 where id = ?1")
				.setParameter(1, r.getId())
				.executeUpdate();

		r.recordSuggestion("summary", Priority.SOON, List.of(), null,
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
		assertThatThrownBy(() -> entityManager.flush())
				.isInstanceOf(OptimisticLockException.class);
	}

	@Test
	void priorityRank_whenNotConfirmed_isNull() {
		Referral r = referralRepository.save(newReferral());
		entityManager.flush();
		entityManager.clear();
		assertThat(referralRepository.findById(r.getId()).orElseThrow().getPriorityRank()).isNull();
	}

	@ParameterizedTest(name = "confirmed {0} -> rank {1}")
	@CsvSource({"URGENT,1", "SOON,2", "ROUTINE,3"})
	void priorityRank_whenConfirmed_isGenerated(Priority priority, short expectedRank) {
		Referral r = newReferral();
		r.recordSuggestion("summary", Priority.SOON, List.of(), null,
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
		r.confirmPriority(priority, "note", "nurse", Instant.parse("2026-01-11T09:00:00Z"));
		referralRepository.save(r);
		entityManager.flush();

		// priority_rank is generated in the database; the value appears on reload.
		entityManager.clear();
		Referral found = referralRepository.findById(r.getId()).orElseThrow();
		assertThat(found.getPriorityRank()).isEqualTo(expectedRank);
	}

	@Test
	void save_whenBooked_persistsAppointmentLink() {
		Referral r = newReferral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.confirmPriority(Priority.SOON, "n", "nurse", Instant.parse("2026-01-11T09:00:00Z"));
		referralRepository.save(r);

		Appointment a = Appointment.bookFor(r, Instant.parse("2026-02-01T09:00:00Z"));
		entityManager.persist(a);
		entityManager.flush();
		entityManager.clear();

		Referral found = referralRepository.findById(r.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(ReferralStatus.BOOKED);
	}
}
