package com.carequeue.triage.service;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.referral.repository.ReferralRepository;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageSuggestionServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

	@Mock
	private ReferralRepository referralRepository;

	@Mock
	private TriageAiClient triageAiClient;

	@Mock
	private PlatformTransactionManager transactionManager;

	private TriageSuggestionService service;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new TriageSuggestionService(referralRepository, triageAiClient, transactionManager, CLOCK);
	}

	private static Referral receivedReferral() {
		Patient p = new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12));
		Clinic c = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
		ReflectionTestUtils.setField(p, "id", 1L);
		ReflectionTestUtils.setField(c, "id", 1L);
		Referral r = Referral.receive(p, c, "Dr Synthetic", "EXT-1", ReferralSource.JSON,
				"synthetic notes", null, Instant.parse("2026-01-10T09:00:00Z"));
		ReflectionTestUtils.setField(r, "id", 42L);
		return r;
	}

	private static TriageSuggestion suggestion() {
		return new TriageSuggestion("short summary", Priority.URGENT, List.of("weight loss"),
				0.87, "rules-v1", SuggestionSource.RULE_BASED_FALLBACK);
	}

	@Test
	void suggest_whenReceived_sendsClinicAgeAndNotesToAiAndAppliesResult() {
		Referral r = receivedReferral();
		when(referralRepository.findWithDetailsById(42L)).thenReturn(Optional.of(r));
		when(referralRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(triageAiClient.suggest(any())).thenReturn(suggestion());

		Referral result = service.suggest(42L);

		ArgumentCaptor<TriageInput> captor = ArgumentCaptor.forClass(TriageInput.class);
		verify(triageAiClient).suggest(captor.capture());
		assertThat(captor.getValue().clinic()).isEqualTo("GASTRO");
		assertThat(captor.getValue().age()).isEqualTo(45); // 1980-05-12 → 45 on 2026-02-01
		assertThat(captor.getValue().clinicalNotes()).isEqualTo("synthetic notes");

		assertThat(result.getAiSummary()).isEqualTo("short summary");
		assertThat(result.getSuggestedPriority()).isEqualTo(Priority.URGENT);
		assertThat(result.getRedFlags()).containsExactly("weight loss");
		assertThat(result.getSuggestionConfidence()).isEqualByComparingTo(new BigDecimal("0.87"));
		assertThat(result.getPromptVersion()).isEqualTo("rules-v1");
		assertThat(result.getSuggestionSource()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
		assertThat(result.getConfirmedPriority()).isNull();
	}

	@Test
	void suggest_whenNotFound_throwsNotFoundWithoutCallingAi() {
		when(referralRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.suggest(99L)).isInstanceOf(NotFoundException.class);
		verify(triageAiClient, never()).suggest(any());
	}

	@Test
	void suggest_whenAlreadyTriaged_throwsInvalidTransitionWithoutCallingAi() {
		Referral r = receivedReferral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "rules-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
		r.confirmPriority(Priority.URGENT, "n", "nurse", Instant.now(CLOCK));
		when(referralRepository.findWithDetailsById(42L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.suggest(42L))
				.isInstanceOf(InvalidStatusTransitionException.class);
		verify(triageAiClient, never()).suggest(any());
	}

	@Test
	void suggest_whenBooked_throwsInvalidTransitionWithoutCallingAi() {
		Referral r = receivedReferral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "rules-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
		r.confirmPriority(Priority.URGENT, "n", "nurse", Instant.now(CLOCK));
		r.markBooked();
		when(referralRepository.findWithDetailsById(42L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.suggest(42L))
				.isInstanceOf(InvalidStatusTransitionException.class);
		verify(triageAiClient, never()).suggest(any());
	}

	@Test
	void suggest_whenRejected_throwsInvalidTransitionWithoutCallingAi() {
		Referral r = receivedReferral();
		r.recordSuggestion("s", Priority.SOON, List.of(), null, "rules-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
		r.reject();
		when(referralRepository.findWithDetailsById(42L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> service.suggest(42L))
				.isInstanceOf(InvalidStatusTransitionException.class);
		verify(triageAiClient, never()).suggest(any());
	}

	@Test
	void suggest_whenVersionChangesBetweenReadAndWrite_throwsStaleVersion() {
		Referral atRead = receivedReferral();
		Referral atWrite = receivedReferral();
		ReflectionTestUtils.setField(atWrite, "version", 7L);
		when(referralRepository.findWithDetailsById(42L))
				.thenReturn(Optional.of(atRead), Optional.of(atWrite));
		when(triageAiClient.suggest(any())).thenReturn(suggestion());

		assertThatThrownBy(() -> service.suggest(42L))
				.isInstanceOf(StaleVersionException.class);
		verify(referralRepository, never()).saveAndFlush(any());
	}
}
