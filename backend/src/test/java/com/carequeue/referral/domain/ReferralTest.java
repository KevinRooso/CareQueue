package com.carequeue.referral.domain;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.patient.domain.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralTest {

	private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

	private static final Clinic CLINIC = new Clinic("GASTRO", "Gastroenterology Clinic", "GASTRO");
	private static final Patient PATIENT =
			new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12));

	private static Referral received() {
		return Referral.receive(PATIENT, CLINIC, "Dr Synthetic", "EXT-1", ReferralSource.JSON,
				"synthetic notes", null, NOW);
	}

	private static Referral inStatus(ReferralStatus status) {
		Referral r = received();
		switch (status) {
			case RECEIVED -> {
			}
			case AI_SUGGESTED -> suggest(r);
			case TRIAGED -> {
				suggest(r);
				r.confirmPriority(Priority.SOON, "note", "nurse", NOW);
			}
			case BOOKED -> {
				suggest(r);
				r.confirmPriority(Priority.SOON, "note", "nurse", NOW);
				Appointment.bookFor(r, NOW.plusSeconds(86400));
			}
			case REJECTED -> r.reject();
		}
		return r;
	}

	private static void suggest(Referral r) {
		r.recordSuggestion("summary", Priority.SOON, List.of(), new BigDecimal("0.60"),
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
	}

	private static void attempt(Referral r, ReferralStatus to) {
		switch (to) {
			case RECEIVED -> throw new UnsupportedOperationException("no transition leads back to RECEIVED");
			case AI_SUGGESTED -> suggest(r);
			case TRIAGED -> r.confirmPriority(Priority.URGENT, "note", "nurse", NOW);
			case BOOKED -> r.markBooked();
			case REJECTED -> r.reject();
		}
	}

	// The allowed edges, written out independently of the enum so the test verifies the table.
	private static final List<String> ALLOWED_EDGES = List.of(
			"RECEIVED>AI_SUGGESTED", "RECEIVED>REJECTED",
			"AI_SUGGESTED>AI_SUGGESTED", "AI_SUGGESTED>TRIAGED", "AI_SUGGESTED>REJECTED",
			"TRIAGED>BOOKED", "TRIAGED>REJECTED");

	static Stream<Arguments> transitions() {
		List<Arguments> args = new java.util.ArrayList<>();
		for (ReferralStatus from : ReferralStatus.values()) {
			for (ReferralStatus to : ReferralStatus.values()) {
				args.add(Arguments.of(from, to, ALLOWED_EDGES.contains(from + ">" + to)));
			}
		}
		return args.stream();
	}

	@ParameterizedTest(name = "{0} -> {1} allowed={2}")
	@MethodSource("transitions")
	void transitionTable_forEveryPair_behavesAsSpecified(ReferralStatus from, ReferralStatus to, boolean allowed) {
		if (to == ReferralStatus.RECEIVED) {
			assertThat(allowed).isFalse();
			return;
		}
		Referral r = inStatus(from);
		if (allowed) {
			attempt(r, to);
			assertThat(r.getStatus()).isEqualTo(to);
		}
		else {
			assertThatThrownBy(() -> attempt(r, to))
					.isInstanceOf(InvalidStatusTransitionException.class)
					.hasMessageContaining(from.name())
					.hasMessageContaining(to.name());
		}
	}

	@Test
	void recordSuggestion_whenCalled_leavesConfirmedPriorityUntouched() {
		Referral r = received();
		r.recordSuggestion("summary", Priority.URGENT, List.of("weight loss"),
				new BigDecimal("0.85"), "triage-v1", SuggestionSource.AI);
		assertThat(r.getConfirmedPriority()).isNull();
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.AI_SUGGESTED);
		assertThat(r.getSuggestedPriority()).isEqualTo(Priority.URGENT);
		assertThat(r.getRedFlags()).containsExactly("weight loss");
		assertThat(r.getPromptVersion()).isEqualTo("triage-v1");
		assertThat(r.getSuggestionSource()).isEqualTo(SuggestionSource.AI);
	}

	@Test
	void recordSuggestion_whenAlreadySuggested_runsAgain() {
		Referral r = inStatus(ReferralStatus.AI_SUGGESTED);
		r.recordSuggestion("newer summary", Priority.ROUTINE, List.of(),
				new BigDecimal("0.30"), "triage-v1", SuggestionSource.AI);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.AI_SUGGESTED);
		assertThat(r.getSuggestedPriority()).isEqualTo(Priority.ROUTINE);
		assertThat(r.getConfirmedPriority()).isNull();
	}

	@Test
	void confirmPriority_whenSuggested_setsAllFields() {
		Referral r = inStatus(ReferralStatus.AI_SUGGESTED);
		r.confirmPriority(Priority.URGENT, "needs 2ww", "nurse.kelly", NOW.plusSeconds(60));
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.TRIAGED);
		assertThat(r.getConfirmedPriority()).isEqualTo(Priority.URGENT);
		assertThat(r.getTriageNote()).isEqualTo("needs 2ww");
		assertThat(r.getTriagedBy()).isEqualTo("nurse.kelly");
		assertThat(r.getTriagedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void recordSuggestion_whenConfidenceNull_isAllowed() {
		Referral r = received();
		r.recordSuggestion("summary", Priority.SOON, List.of(), null,
				"triage-v1", SuggestionSource.RULE_BASED_FALLBACK);
		assertThat(r.getSuggestionConfidence()).isNull();
	}

	@Test
	void recordSuggestion_whenConfidenceOutOfRange_throws() {
		Referral r = received();
		assertThatThrownBy(() -> r.recordSuggestion("s", Priority.SOON, List.of(),
				new BigDecimal("1.50"), "triage-v1", SuggestionSource.AI))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> r.recordSuggestion("s", Priority.SOON, List.of(),
				new BigDecimal("-0.01"), "triage-v1", SuggestionSource.AI))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recordSuggestion_whenSourceNull_throwsAndStaysReceived() {
		Referral r = received();
		assertThatThrownBy(() -> r.recordSuggestion("s", Priority.SOON, List.of(),
				null, "triage-v1", null))
				.isInstanceOf(NullPointerException.class);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.RECEIVED);
	}

	@Test
	void recordSuggestion_whenCallerMutatesRedFlags_entityKeepsCopy() {
		Referral r = received();
		List<String> flags = new java.util.ArrayList<>(List.of("weight loss"));
		r.recordSuggestion("s", Priority.URGENT, flags, null, "triage-v1", SuggestionSource.AI);
		flags.add("tampered");
		assertThat(r.getRedFlags()).containsExactly("weight loss");
	}

	@Test
	void confirmPriority_whenPriorityNull_throwsAndStaysSuggested() {
		Referral r = inStatus(ReferralStatus.AI_SUGGESTED);
		assertThatThrownBy(() -> r.confirmPriority(null, "note", "nurse", NOW))
				.isInstanceOf(NullPointerException.class);
		assertThat(r.getStatus()).isEqualTo(ReferralStatus.AI_SUGGESTED);
	}
}
