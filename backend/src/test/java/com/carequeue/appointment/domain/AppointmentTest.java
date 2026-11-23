package com.carequeue.appointment.domain;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTest {

	private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

	private static Referral newReferral() {
		return Referral.receive(
				new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12)),
				new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO"),
				"Dr Synthetic", "EXT-1", ReferralSource.JSON, "synthetic notes", null, NOW);
	}

	private static Referral triaged() {
		Referral r = newReferral();
		r.recordSuggestion("summary", Priority.SOON, List.of(), new BigDecimal("0.60"),
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
		r.confirmPriority(Priority.SOON, "note", "nurse", NOW);
		return r;
	}

	@Test
	void bookFor_whenTriaged_marksReferralBooked() {
		Referral r = triaged();
		Appointment a = Appointment.bookFor(r, NOW.plusSeconds(86400));
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.BOOKED);
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(a.getReferral()).isSameAs(r);
	}

	@Test
	void bookFor_whenNotTriaged_throws() {
		Referral received = newReferral();
		assertThatThrownBy(() -> Appointment.bookFor(received, NOW.plusSeconds(86400)))
				.isInstanceOf(InvalidStatusTransitionException.class);

		Referral suggested = newReferral();
		suggested.recordSuggestion("summary", Priority.SOON, List.of(), null,
				"triage-v1", SuggestionSource.AI);
		assertThatThrownBy(() -> Appointment.bookFor(suggested, NOW.plusSeconds(86400)))
				.isInstanceOf(InvalidStatusTransitionException.class);

		Referral rejected = newReferral();
		rejected.reject();
		assertThatThrownBy(() -> Appointment.bookFor(rejected, NOW.plusSeconds(86400)))
				.isInstanceOf(InvalidStatusTransitionException.class);
	}
}
