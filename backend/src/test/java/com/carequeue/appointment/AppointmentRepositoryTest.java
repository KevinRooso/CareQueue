package com.carequeue.appointment;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.domain.AppointmentStatus;
import com.carequeue.appointment.repository.AppointmentRepository;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class AppointmentRepositoryTest {

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void save_whenReloaded_restoresFieldsAndReferral() {
		Patient p = patientRepository.save(
				new Patient("MRN-300001", "Jo", "Test", LocalDate.of(1988, 7, 4)));
		Clinic c = clinicRepository.save(new Clinic("APPT", "Appointment Clinic", "TESTING"));
		Referral r = Referral.receive(p, c, "Dr Synthetic", "APPT-1",
				ReferralSource.JSON, "synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z"));
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.confirmPriority(Priority.SOON, "n", "nurse", Instant.parse("2026-01-11T09:00:00Z"));
		referralRepository.save(r);

		Appointment saved = appointmentRepository.save(
				Appointment.bookFor(r, Instant.parse("2026-02-15T14:30:00Z")));
		entityManager.flush();
		entityManager.clear();

		Appointment found = appointmentRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(found.getStartsAt()).isEqualTo(Instant.parse("2026-02-15T14:30:00Z"));
		assertThat(found.getReferral().getId()).isEqualTo(r.getId());
		assertThat(found.getReferral().getStatus()).isEqualTo(ReferralStatus.BOOKED);
	}
}
