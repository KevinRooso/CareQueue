package com.carequeue.appointment.service;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.repository.AppointmentRepository;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

	private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");
	private static final Instant STARTS = Instant.parse("2026-02-20T09:00:00Z");

	@Mock
	private ReferralRepository referralRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@InjectMocks
	private AppointmentService service;

	private Referral receivedReferral() {
		return Referral.receive(
				new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12)),
				new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO"),
				"Dr Synthetic", "EXT-1", ReferralSource.JSON, "synthetic notes", null,
				Instant.parse("2026-01-10T09:00:00Z"));
	}

	@Test
	void book_whenReferralTriaged_createsBookedAppointment() {
		Referral r = receivedReferral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "triage-v1", SuggestionSource.AI);
		r.confirmPriority(Priority.URGENT, "n", "nurse", NOW);
		when(referralRepository.findById(1L)).thenReturn(Optional.of(r));
		when(appointmentRepository.save(any(Appointment.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		Appointment a = service.book(1L, STARTS);

		assertThat(a.getStartsAt()).isEqualTo(STARTS);
		assertThat(a.getReferral()).isSameAs(r);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.BOOKED);
	}

	@Test
	void book_whenReferralNotTriaged_throwsInvalidTransition() {
		Referral r = receivedReferral();
		when(referralRepository.findById(1L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.book(1L, STARTS))
				.isInstanceOf(InvalidStatusTransitionException.class);
		verify(appointmentRepository, never()).save(any());
	}

	@Test
	void book_whenReferralNotFound_throwsNotFound() {
		when(referralRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.book(99L, STARTS))
				.isInstanceOf(NotFoundException.class);
	}
}
