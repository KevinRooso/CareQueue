package com.carequeue.triage.service;

/**
 * The model answered, but the answer is not usable: not JSON, or JSON that breaks the
 * {@code TriageSuggestion} rules. Never retried (asking again rarely fixes it and costs quota);
 * {@link ResilientTriageAiClient} falls back to the rules instead.
 *
 * <p>Messages describe the rule that failed only. They never contain the model's reply,
 * because the reply can echo clinical notes.
 */
public class InvalidModelOutputException extends RuntimeException {

	public InvalidModelOutputException(String message) {
		super(message);
	}
}
