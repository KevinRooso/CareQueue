package com.carequeue.triage.service;

import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;

/**
 * Suggests a triage priority for a referral. Implementations only return a suggestion;
 * they never change a referral. A clinician must confirm the priority (ADR-005).
 *
 * <p>The rest of the app depends on this interface only. The {@code @Primary} bean is
 * {@link ResilientTriageAiClient}, which tries the model and falls back to the rules.
 */
public interface TriageAiClient {

	TriageSuggestion suggest(TriageInput input);
}
