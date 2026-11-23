package com.carequeue.triage.service;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import com.carequeue.triage.eval.EvalCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedTriageAiClientTest {

	private static final Logger log = LoggerFactory.getLogger(RuleBasedTriageAiClientTest.class);

	private final RuleBasedTriageAiClient client = new RuleBasedTriageAiClient();

	static List<EvalCase> datasetCases() {
		return EvalCase.loadDataset();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("datasetCases")
	void suggest_whenDatasetCaseIsUrgent_neverMissesIt(EvalCase evalCase) {
		TriageSuggestion suggestion = client.suggest(evalCase.toInput());

		// The safety net: urgent recall must be 100%. Non-urgent cases are only reported (see below).
		if (evalCase.expectedPriority() == Priority.URGENT) {
			assertThat(suggestion.suggestedPriority()).as("urgent case %s", evalCase.id()).isEqualTo(Priority.URGENT);
			assertThat(suggestion.redFlags()).as("red flags for %s", evalCase.id()).isNotEmpty();
		}
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
	}

	@Test
	void suggest_overWholeDataset_reportsAccuracyAndHasFullUrgentRecall() {
		List<EvalCase> cases = datasetCases();
		long correct = cases.stream()
				.filter(c -> client.suggest(c.toInput()).suggestedPriority() == c.expectedPriority())
				.count();
		long urgent = cases.stream().filter(c -> c.expectedPriority() == Priority.URGENT).count();
		long urgentHit = cases.stream()
				.filter(c -> c.expectedPriority() == Priority.URGENT)
				.filter(c -> client.suggest(c.toInput()).suggestedPriority() == Priority.URGENT)
				.count();

		// Accuracy is reported, not asserted: the rules are a safety net, not a tuned classifier.
		log.info("Rule-based triage on dataset: urgent recall {}/{}, accuracy {}/{}", urgentHit, urgent, correct,
				cases.size());
		assertThat(cases).hasSize(10);
		assertThat(urgentHit).isEqualTo(urgent);
	}

	@Test
	void suggest_whenRedFlagsAreNegated_isNotUrgent() {
		// T03 from the dataset: "No weight loss, no difficulty swallowing" must not count as red flags.
		TriageSuggestion suggestion = client.suggest(new TriageInput("GASTRO", 46,
				"3 months of heartburn and upper abdominal pain not settling on 8 weeks of omeprazole. "
						+ "No weight loss, no difficulty swallowing. Requesting specialist review and "
						+ "consideration of endoscopy."));

		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.SOON);
		assertThat(suggestion.redFlags()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Denies rectal bleeding. Long-standing mild reflux.",
			"Without weight loss or vomiting blood. Stable symptoms.",
			"Not losing weight. Occasional indigestion, wants reassurance." })
	void suggest_whenOnlyNegatedRedFlags_isNotUrgent(String notes) {
		assertThat(client.suggest(new TriageInput("GASTRO", 40, notes)).suggestedPriority())
				.isNotEqualTo(Priority.URGENT);
	}

	@Test
	void suggest_whenContrastWordEndsNegation_isUrgent() {
		TriageSuggestion suggestion = client.suggest(new TriageInput("GASTRO", 70,
				"No fever but weight loss of 4kg over two months."));

		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.URGENT);
		assertThat(suggestion.redFlags()).containsExactly("weight loss");
	}

	@Test
	void suggest_whenChestPainAndExertionAreSplitByComma_isUrgent() {
		TriageSuggestion suggestion = client.suggest(new TriageInput("CARDIOLOGY", 64,
				"Chest pain, worse when walking up stairs, for two weeks."));

		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.URGENT);
	}

	@Test
	void suggest_whenNothingMatches_defaultsToSoon() {
		assertThat(client.suggest(new TriageInput("DERMATOLOGY", 33, "Please assess the rash.")).suggestedPriority())
				.isEqualTo(Priority.SOON);
	}

	@Test
	void suggest_whenSoonAndRoutineIndicatorsBothMatch_moreUrgentWins() {
		assertThat(client.suggest(new TriageInput("CARDIOLOGY", 72,
				"Moderate aortic stenosis. Due for routine surveillance review.")).suggestedPriority())
				.isEqualTo(Priority.SOON);
	}

	@Test
	void suggest_whenCalled_returnsRuleBasedShape() {
		TriageSuggestion suggestion = client.suggest(new TriageInput("GASTRO", 58,
				"Six weeks of change in bowel habit with 5kg unintentional weight loss. Bloods show anaemia."));

		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
		assertThat(suggestion.promptVersion()).isEqualTo("rules-v1");
		assertThat(suggestion.confidence()).isEqualTo(0.5);
		assertThat(suggestion.summary())
				.isEqualTo("Six weeks of change in bowel habit with 5kg unintentional weight loss.");
		assertThat(suggestion.redFlags()).containsExactly("weight loss", "anaemia");
	}

	@Test
	void suggest_whenFirstSentenceIsLong_capsSummaryAtFortyWords() {
		String longSentence = String.join(" ", java.util.Collections.nCopies(50, "word")) + ".";

		String summary = client.suggest(new TriageInput("GASTRO", 50, longSentence)).summary();

		assertThat(summary.split("\\s+")).hasSize(RuleBasedTriageAiClient.MAX_SUMMARY_WORDS + 1); // 40 words + "…"
		assertThat(summary).endsWith("…");
	}

	@Test
	void suggest_whenNotesBlank_stillReturnsSuggestion() {
		TriageSuggestion suggestion = client.suggest(new TriageInput("GASTRO", 50, "   "));

		assertThat(suggestion.summary()).isNotBlank();
		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.SOON);
	}
}
