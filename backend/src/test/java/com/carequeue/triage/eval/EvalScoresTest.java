package com.carequeue.triage.eval;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageSuggestion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The eval's scoring maths, tested without calling any client. */
class EvalScoresTest {

	private static EvalCase evalCase(String id, Priority expected, List<String> must, List<String> mustNot) {
		return new EvalCase(id, "GASTRO", 50, "synthetic notes", expected, must, mustNot);
	}

	private static CaseResult result(EvalCase evalCase, Priority got, String summary, List<String> flags,
			SuggestionSource source, long millis) {
		return new CaseResult(evalCase,
				new TriageSuggestion(summary, got, flags, 0.8, "triage-v1", source), millis * 1_000_000);
	}

	@Test
	void mentions_whenAlternatives_matchesAnyCaseInsensitively() {
		assertThat(EvalScores.mentions("Partial response to a PPI", "omeprazole|PPI")).isTrue();
		assertThat(EvalScores.mentions("Iron deficiency ANAEMIA", "anaemia")).isTrue();
		assertThat(EvalScores.mentions("bloating", "omeprazole|PPI")).isFalse();
	}

	@Test
	void from_whenMixedResults_computesEveryMetric() {
		List<CaseResult> results = List.of(
				result(evalCase("T01", Priority.URGENT, List.of("weight loss", "anaemia"), List.of("vomiting")),
						Priority.URGENT, "Weight loss.", List.of("anaemia"), SuggestionSource.AI, 100),
				result(evalCase("T02", Priority.URGENT, List.of("swallowing|dysphagia"), List.of()),
						Priority.SOON, "Food sticking.", List.of(), SuggestionSource.AI, 300),
				result(evalCase("T03", Priority.ROUTINE, List.of("eczema"), List.of("infection")),
						Priority.ROUTINE, "Mild eczema with infection.", List.of(), SuggestionSource.RULE_BASED_FALLBACK,
						200));

		EvalScores scores = EvalScores.from(results);

		assertThat(scores.urgentHit()).isEqualTo(1);
		assertThat(scores.urgentTotal()).isEqualTo(2);
		assertThat(scores.urgentRecallPasses()).isFalse();
		assertThat(scores.correct()).isEqualTo(2);
		assertThat(scores.aiAnswered()).isEqualTo(2);
		assertThat(scores.mentionHit()).isEqualTo(3);
		assertThat(scores.mentionTotal()).isEqualTo(4);
		assertThat(scores.unsupported()).isEqualTo(1);
		assertThat(scores.p50Millis()).isEqualTo(200.0);
		assertThat(scores.p95Millis()).isEqualTo(300.0);
		assertThat(scores.failures()).containsExactly(
				"T02: expected URGENT, got SOON; missing \"swallowing|dysphagia\" (source AI)",
				"T03: unsupported \"infection\" (source RULE_BASED_FALLBACK)");
	}

	@Test
	void mode_whenNoKeyOrNoAiAnswer_isFallback() {
		EvalScores none = EvalScores.from(List.of(result(evalCase("T01", Priority.SOON, List.of(), List.of()),
				Priority.SOON, "s", List.of(), SuggestionSource.RULE_BASED_FALLBACK, 1)));

		assertThat(EvalReport.mode(false, none)).isEqualTo(EvalReport.Mode.FALLBACK_NO_KEY);
		assertThat(EvalReport.mode(true, none)).isEqualTo(EvalReport.Mode.FALLBACK_MODEL_UNAVAILABLE);
	}

	@Test
	void fileName_whenModelOrFallback_followsNamingRule() {
		LocalDate date = LocalDate.of(2026, 11, 23);

		assertThat(EvalReport.fileName(date, "triage-v1", EvalReport.Mode.MODEL, "llama-3.3-70b-versatile"))
				.isEqualTo("2026-11-23-triage-v1-llama-3-3-70b-versatile.md");
		assertThat(EvalReport.fileName(date, "triage-v1", EvalReport.Mode.FALLBACK_NO_KEY, "llama-3.3-70b-versatile"))
				.isEqualTo("2026-11-23-triage-v1-fallback.md");
		assertThat(EvalReport.fileName(date, "triage-v1", EvalReport.Mode.MODEL, "openai/gpt-oss-120b"))
				.isEqualTo("2026-11-23-triage-v1-openai-gpt-oss-120b.md");
	}

	@Test
	void render_whenFallbackMode_showsBannerAndClient() {
		List<CaseResult> results = List.of(result(evalCase("T01", Priority.URGENT, List.of("weight loss"), List.of()),
				Priority.URGENT, "Weight loss.", List.of("weight loss"), SuggestionSource.RULE_BASED_FALLBACK, 1));

		String md = EvalReport.render(LocalDate.of(2026, 11, 23), "triage-v1", EvalReport.Mode.FALLBACK_NO_KEY,
				"llama-3.3-70b-versatile", "https://api.groq.com/openai/v1", EvalScores.from(results), results);

		assertThat(md)
				.startsWith("# Triage eval – triage-v1 – 2026-11-23")
				.contains("FALLBACK MODE – this is not a model evaluation")
				.contains("- Client: RuleBasedTriageAiClient")
				.contains("| Urgent recall | 1/1 (100%) | ✅ |")
				.contains("## Failures\n- none");
	}
}
