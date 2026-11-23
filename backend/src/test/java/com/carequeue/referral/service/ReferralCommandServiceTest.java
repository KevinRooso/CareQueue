package com.carequeue.referral.service;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");
	private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

	@Mock
	private ReferralRepository referralRepository;

	private ReferralCommandService service;

	@BeforeEach
	void setUp() {
		service = new ReferralCommandService(referralRepository, FIXED);
		lenient().when(referralRepository.saveAndFlush(any(Referral.class)))
				.thenAnswer(inv -> inv.getArgument(0));
	}

	private Referral suggestedReferral() {
		Referral r = Referral.receive(
				new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12)),
				new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO"),
				"Dr Synthetic", "EXT-1", ReferralSource.JSON, "synthetic notes", null,
				Instant.parse("2026-01-10T09:00:00Z"));
		r.recordSuggestion("summary", Priority.SOON, List.of(), new BigDecimal("0.60"),
				"triage-v1", SuggestionSource.AI);
		return r;
	}

	@Test
	void confirm_whenVersionMatches_setsFieldsAndFlushes() {
		Referral r = suggestedReferral();
		when(referralRepository.findWithDetailsById(1L)).thenReturn(Optional.of(r));

		Referral result = service.confirm(1L, Priority.URGENT, "2ww pathway", 0L, "nurse.kelly");

		assertThat(result.getStatus()).isEqualTo(ReferralStatus.TRIAGED);
		assertThat(result.getConfirmedPriority()).isEqualTo(Priority.URGENT);
		assertThat(result.getTriageNote()).isEqualTo("2ww pathway");
		assertThat(result.getTriagedBy()).isEqualTo("nurse.kelly");
		assertThat(result.getTriagedAt()).isEqualTo(NOW);
		verify(referralRepository).saveAndFlush(r);
	}

	@Test
	void confirm_whenVersionIsStale_throwsStaleVersion() {
		Referral r = suggestedReferral();
		when(referralRepository.findWithDetailsById(1L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.confirm(1L, Priority.URGENT, "n", 99L, "nurse"))
				.isInstanceOf(StaleVersionException.class);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.AI_SUGGESTED);
		verify(referralRepository, never()).saveAndFlush(any());
	}

	@Test
	void confirm_whenNotFound_throwsNotFound() {
		when(referralRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.confirm(99L, Priority.URGENT, "n", 0L, "nurse"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void reject_whenVersionMatches_marksRejected() {
		Referral r = suggestedReferral();
		when(referralRepository.findWithDetailsById(1L)).thenReturn(Optional.of(r));

		Referral result = service.reject(1L, 0L);

		assertThat(result.getStatus()).isEqualTo(ReferralStatus.REJECTED);
		verify(referralRepository).saveAndFlush(r);
	}

	@Test
	void reject_whenVersionIsStale_throwsStaleVersion() {
		Referral r = suggestedReferral();
		when(referralRepository.findWithDetailsById(1L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.reject(1L, 7L))
				.isInstanceOf(StaleVersionException.class);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.AI_SUGGESTED);
		verify(referralRepository, never()).saveAndFlush(any());
	}

	@Test
	void reject_whenNotFound_throwsNotFound() {
		when(referralRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reject(99L, 0L))
				.isInstanceOf(NotFoundException.class);
	}
}
