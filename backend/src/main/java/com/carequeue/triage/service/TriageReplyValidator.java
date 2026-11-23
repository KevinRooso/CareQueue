package com.carequeue.triage.service;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageSuggestion;

import java.util.List;
import java.util.Set;

/**
 * Turns the model's parsed reply into a {@link TriageSuggestion} (source AI), or throws
 * {@link InvalidModelOutputException} if any rule is broken. The model is never trusted blindly:
 * even valid JSON can carry a made-up priority, an empty summary or an out-of-range confidence.
 */
public class TriageReplyValidator {

	static final int MAX_SUMMARY_WORDS = 80;
	static final int MAX_RED_FLAGS = 10;
	static final int MAX_RED_FLAG_LENGTH = 120;

	private static final Set<String> PRIORITIES = Set.of("URGENT", "SOON", "ROUTINE");

	public TriageSuggestion validate(ModelTriageReply reply, String promptVersion) {
		if (reply == null) {
			throw new InvalidModelOutputException("reply is empty");
		}
		String summary = reply.summary();
		if (summary == null || summary.isBlank()) {
			throw new InvalidModelOutputException("summary is blank");
		}
		summary = summary.strip();
		if (summary.split("\\s+").length > MAX_SUMMARY_WORDS) {
			throw new InvalidModelOutputException("summary is longer than " + MAX_SUMMARY_WORDS + " words");
		}

		String priority = reply.suggestedPriority();
		if (priority == null || !PRIORITIES.contains(priority)) {
			throw new InvalidModelOutputException("suggestedPriority must be URGENT, SOON or ROUTINE");
		}

		List<String> redFlags = reply.redFlags();
		if (redFlags == null) {
			throw new InvalidModelOutputException("redFlags is missing");
		}
		if (redFlags.size() > MAX_RED_FLAGS) {
			throw new InvalidModelOutputException("more than " + MAX_RED_FLAGS + " redFlags");
		}
		for (String flag : redFlags) {
			if (flag == null || flag.isBlank()) {
				throw new InvalidModelOutputException("a redFlag is blank");
			}
			if (flag.strip().length() > MAX_RED_FLAG_LENGTH) {
				throw new InvalidModelOutputException("a redFlag is longer than " + MAX_RED_FLAG_LENGTH + " characters");
			}
		}

		Double confidence = reply.confidence();
		if (confidence == null || confidence.isNaN() || confidence < 0.0 || confidence > 1.0) {
			throw new InvalidModelOutputException("confidence must be a number between 0 and 1");
		}

		return new TriageSuggestion(
				summary,
				Priority.valueOf(priority),
				redFlags.stream().map(String::strip).toList(),
				confidence,
				promptVersion,
				SuggestionSource.AI);
	}
}
