package com.carequeue.triage.domain;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;

import java.util.List;
import java.util.Objects;

/**
 * A triage suggestion for a clinician to confirm. It is never applied automatically:
 * only a clinician's confirm sets the referral's confirmed priority (ADR-005).
 *
 * @param summary           short plain-English summary of the referral
 * @param suggestedPriority URGENT, SOON or ROUTINE
 * @param redFlags          red-flag phrases supported by the notes (may be empty)
 * @param confidence        0..1; the model's own estimate, or a fixed value for the rules
 * @param promptVersion     e.g. "triage-v1" for the model, "rules-v1" for the rule-based client
 * @param source            AI or RULE_BASED_FALLBACK
 */
public record TriageSuggestion(
		String summary,
		Priority suggestedPriority,
		List<String> redFlags,
		double confidence,
		String promptVersion,
		SuggestionSource source) {

	public TriageSuggestion {
		Objects.requireNonNull(summary, "summary is required");
		Objects.requireNonNull(suggestedPriority, "suggestedPriority is required");
		Objects.requireNonNull(promptVersion, "promptVersion is required");
		Objects.requireNonNull(source, "source is required");
		redFlags = redFlags == null ? List.of() : List.copyOf(redFlags);
	}
}
