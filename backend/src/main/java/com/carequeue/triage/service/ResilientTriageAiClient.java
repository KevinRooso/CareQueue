package com.carequeue.triage.service;

import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code TriageAiClient} the rest of the app uses ({@code @Primary}). It always returns a
 * suggestion:
 * <ol>
 *   <li>AI not configured (no API key): straight to the rules, no HTTP call at all.</li>
 *   <li>Otherwise ask the model. Transient failures are retried inside the AI client's proxy.</li>
 *   <li>Invalid reply, or any failure left after the retries: fall back to the rules.</li>
 * </ol>
 * Every outcome is counted in {@code carequeue.triage.suggestions{source, reason}}. Logs carry the
 * exception class only, never the message, the notes or the reply.
 */
public class ResilientTriageAiClient implements TriageAiClient {

	static final String METRIC = "carequeue.triage.suggestions";

	private static final Logger log = LoggerFactory.getLogger(ResilientTriageAiClient.class);

	private final TriageAiClient aiClient;
	private final TriageAiClient fallback;
	private final boolean aiEnabled;

	private final Counter aiOk;
	private final Counter notConfigured;
	private final Counter invalidOutput;
	private final Counter error;

	/**
	 * @param aiClient  the model client; must be the Spring bean (proxy) so that {@code @Retryable} applies
	 * @param fallback  the rule-based client
	 * @param aiEnabled false when no API key is configured
	 */
	public ResilientTriageAiClient(TriageAiClient aiClient, TriageAiClient fallback, MeterRegistry meterRegistry,
			boolean aiEnabled) {
		this.aiClient = aiClient;
		this.fallback = fallback;
		this.aiEnabled = aiEnabled;
		this.aiOk = counter(meterRegistry, SuggestionSource.AI, "ok");
		this.notConfigured = counter(meterRegistry, SuggestionSource.RULE_BASED_FALLBACK, "not_configured");
		this.invalidOutput = counter(meterRegistry, SuggestionSource.RULE_BASED_FALLBACK, "invalid_output");
		this.error = counter(meterRegistry, SuggestionSource.RULE_BASED_FALLBACK, "error");
	}

	public boolean isAiEnabled() {
		return aiEnabled;
	}

	@Override
	public TriageSuggestion suggest(TriageInput input) {
		if (!aiEnabled) {
			notConfigured.increment();
			return fallback.suggest(input);
		}
		try {
			TriageSuggestion suggestion = aiClient.suggest(input);
			aiOk.increment();
			return suggestion;
		}
		catch (InvalidModelOutputException ex) {
			log.warn("AI triage reply was invalid, using rule-based fallback (reason={})", ex.getClass().getSimpleName());
			invalidOutput.increment();
		}
		catch (RuntimeException ex) {
			log.warn("AI triage call failed, using rule-based fallback (reason={})", ex.getClass().getSimpleName());
			error.increment();
		}
		return fallback.suggest(input);
	}

	private static Counter counter(MeterRegistry registry, SuggestionSource source, String reason) {
		return Counter.builder(METRIC)
				.description("Triage suggestions by source and reason")
				.tag("source", source.name())
				.tag("reason", reason)
				.register(registry);
	}
}
