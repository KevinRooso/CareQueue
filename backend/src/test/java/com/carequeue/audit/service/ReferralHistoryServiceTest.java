package com.carequeue.audit.service;

import com.carequeue.audit.api.ReferralRevisionDto.FieldChange;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.patient.domain.Patient;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.SuggestionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReferralHistoryServiceTest {

	private static final Patient PATIENT =
			new Patient("MRN-H1", "Ann", "Test", LocalDate.of(1990, 1, 1));
	private static final Clinic CLINIC = new Clinic("DERM", "Dermatology", "DERM");

	private static Referral received() {
		return Referral.receive(PATIENT, CLINIC, "Dr. Test", "REF-H1", ReferralSource.JSON,
				"notes", "{}", Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static Map<String, FieldChange> byField(List<FieldChange> changes) {
		return changes.stream().collect(Collectors.toMap(FieldChange::field, Function.identity()));
	}

	@Test
	void changesBetween_whenCreated_listsNonNullInitialValues() {
		List<FieldChange> changes = ReferralHistoryService.changesBetween(null, received());

		Map<String, FieldChange> byField = byField(changes);
		assertThat(byField.get("status").from()).isNull();
		assertThat(byField.get("status").to()).isEqualTo("RECEIVED");
		// other tracked fields are null at import, so nothing else shows up
		assertThat(changes).extracting(FieldChange::field).containsExactly("status");
	}

	@Test
	void changesBetween_whenSuggested_showsEveryAiField() {
		Referral previous = received();
		Referral current = received();
		current.recordSuggestion("short summary", Priority.URGENT, List.of("weight loss", "fever"),
				new BigDecimal("0.50"), "rules-v1", SuggestionSource.RULE_BASED_FALLBACK);

		Map<String, FieldChange> byField = byField(ReferralHistoryService.changesBetween(previous, current));

		assertThat(byField.get("status").to()).isEqualTo("AI_SUGGESTED");
		assertThat(byField.get("suggestedPriority").to()).isEqualTo("URGENT");
		assertThat(byField.get("suggestionSource").to()).isEqualTo("RULE_BASED_FALLBACK");
		assertThat(byField.get("suggestionConfidence").to()).isEqualTo("0.50");
		assertThat(byField.get("promptVersion").to()).isEqualTo("rules-v1");
		assertThat(byField.get("aiSummary").to()).isEqualTo("short summary");
		assertThat(byField.get("redFlags").from()).isNull();
		assertThat(byField.get("redFlags").to()).isEqualTo("weight loss, fever");
	}

	@Test
	void changesBetween_whenConfirmed_showsClinicianFields() {
		Instant at = Instant.parse("2026-02-01T10:00:00Z");
		Referral previous = received();
		previous.recordSuggestion("s", Priority.ROUTINE, List.of(), null, "rules-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
		Referral current = received();
		current.recordSuggestion("s", Priority.ROUTINE, List.of(), null, "rules-v1",
				SuggestionSource.RULE_BASED_FALLBACK);
		current.confirmPriority(Priority.URGENT, "escalating", "nurse.test", at);

		Map<String, FieldChange> byField = byField(ReferralHistoryService.changesBetween(previous, current));

		assertThat(byField.get("status").to()).isEqualTo("TRIAGED");
		assertThat(byField.get("confirmedPriority").from()).isNull();
		assertThat(byField.get("confirmedPriority").to()).isEqualTo("URGENT");
		assertThat(byField.get("triageNote").to()).isEqualTo("escalating");
		assertThat(byField.get("triagedBy").to()).isEqualTo("nurse.test");
		assertThat(byField.get("triagedAt").to()).isEqualTo("2026-02-01T10:00:00Z");
	}

	@Test
	void changesBetween_whenNothingChanged_returnsEmpty() {
		Referral a = received();
		Referral b = received();

		assertThat(ReferralHistoryService.changesBetween(a, b)).isEmpty();
	}
}
