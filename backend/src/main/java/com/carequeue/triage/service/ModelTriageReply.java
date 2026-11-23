package com.carequeue.triage.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The raw shape of the model's JSON reply, before validation. Loose types on purpose
 * (priority as a String, confidence as a nullable Double) so that a bad value becomes a clear
 * validation error in {@link TriageReplyValidator} instead of a parse error.
 * Only used to parse the model's reply; the rest of the app sees {@code TriageSuggestion}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelTriageReply(
		String summary,
		String suggestedPriority,
		List<String> redFlags,
		Double confidence) {
}
