package com.carequeue.common.demo;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.repository.AppointmentRepository;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds 25 obviously synthetic patients and referrals for the `demo` profile.
 * Idempotent: a second run does nothing.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
	private static final int COUNT = 25;

	private final PatientRepository patientRepository;
	private final ReferralRepository referralRepository;
	private final AppointmentRepository appointmentRepository;
	private final ClinicRepository clinicRepository;
	private final Clock clock;

	public DemoDataSeeder(PatientRepository patientRepository, ReferralRepository referralRepository,
			AppointmentRepository appointmentRepository, ClinicRepository clinicRepository, Clock clock) {
		this.patientRepository = patientRepository;
		this.referralRepository = referralRepository;
		this.appointmentRepository = appointmentRepository;
		this.clinicRepository = clinicRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (patientRepository.existsByMrn("MRN-900001")) {
			log.info("Demo data already seeded, skipping");
			return;
		}

		Clinic gastro = clinicRepository.findByCode("GASTRO").orElseThrow();
		Clinic cardiology = clinicRepository.findByCode("CARDIOLOGY").orElseThrow();
		Clinic dermatology = clinicRepository.findByCode("DERMATOLOGY").orElseThrow();
		Clinic[] clinics = {gastro, cardiology, dermatology};

		String[] firstNames = {"Alex", "Sam", "Jordan", "Casey", "Riley"};
		String[] lastNames = {"Sample", "Test", "Demo", "Example", "Placeholder"};
		ReferralSource[] sources = {ReferralSource.JSON, ReferralSource.XML, ReferralSource.MANUAL};
		String[] notes = {
				"Worsening abdominal pain with unexplained weight loss over 8 weeks.",
				"Stable intermittent indigestion for review.",
				"New palpitations on exertion reported by GP.",
				"Routine medication review for long-standing reflux.",
				"Rectal bleeding noted for 3 weeks, needs assessment.",
				"Persistent itchy rash, no improvement with emollients."};

		Instant now = Instant.now(clock);
		for (int i = 1; i <= COUNT; i++) {
			Patient patient = patientRepository.save(new Patient(
					String.format("MRN-900%03d", i),
					firstNames[i % firstNames.length],
					lastNames[i % lastNames.length],
					LocalDate.of(1950 + (i % 50), (i % 12) + 1, (i % 28) + 1)));

			Referral referral = referralRepository.save(Referral.receive(
					patient,
					clinics[i % clinics.length],
					"Dr Synthetic " + i,
					String.format("DEMO-2026-%04d", i),
					sources[i % sources.length],
					notes[i % notes.length],
					i % 3 == 0 ? null : "{\"externalRef\":\"" + String.format("DEMO-2026-%04d", i) + "\"}",
					now.minus(Duration.ofDays(i % 60))));

			applyStatus(referral, i, now);
		}

		log.info("Seeded {} synthetic patients and referrals", COUNT);
	}

	private void applyStatus(Referral referral, int i, Instant now) {
		// 6 RECEIVED, 6 AI_SUGGESTED, 8 TRIAGED, 3 BOOKED, 2 REJECTED
		if (i <= 6) {
			return;
		}
		if (i <= 12) {
			suggest(referral);
			return;
		}
		if (i <= 14) {
			suggest(referral);
			referral.reject();
			return;
		}
		Priority priority = switch (i % 3) {
			case 0 -> Priority.URGENT;
			case 1 -> Priority.SOON;
			default -> Priority.ROUTINE;
		};
		suggest(referral);
		referral.confirmPriority(priority, "synthetic triage note", "nurse", now);
		if (i <= 17) {
			appointmentRepository.save(Appointment.bookFor(referral, now.plus(Duration.ofDays(3 + (i % 18)))));
		}
	}

	private void suggest(Referral referral) {
		referral.recordSuggestion(
				"Synthetic summary of the referral.",
				Priority.SOON,
				List.of(),
				new BigDecimal("0.60"),
				"triage-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
	}
}
