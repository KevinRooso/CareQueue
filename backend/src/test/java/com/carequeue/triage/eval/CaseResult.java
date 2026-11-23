package com.carequeue.triage.eval;

import com.carequeue.triage.domain.TriageSuggestion;

/** One eval case and what the triage client answered, with how long the call took. */
public record CaseResult(EvalCase evalCase, TriageSuggestion suggestion, long latencyNanos) {

	/** Summary and red flags: the text that mustMention / mustNotMention are checked against. */
	public String searchableText() {
		return suggestion.summary() + "\n" + String.join("\n", suggestion.redFlags());
	}

	public double latencyMillis() {
		return latencyNanos / 1_000_000.0;
	}
}
