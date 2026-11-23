package com.carequeue.triage.eval;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Builds the markdown report and its file name (format in evals/README.md). */
public final class EvalReport {

	public enum Mode {
		/** A real model answered at least one case: thresholds are enforced. */
		MODEL,
		/** No API key: every case went to the rules. Not a model evaluation. */
		FALLBACK_NO_KEY,
		/** A key is set but the model never answered (bad key, rate limit, wrong model, network). */
		FALLBACK_MODEL_UNAVAILABLE
	}

	private EvalReport() {
	}

	public static Mode mode(boolean aiEnabled, EvalScores scores) {
		if (!aiEnabled) {
			return Mode.FALLBACK_NO_KEY;
		}
		return scores.aiAnswered() > 0 ? Mode.MODEL : Mode.FALLBACK_MODEL_UNAVAILABLE;
	}

	/** {@code <yyyy-MM-dd>-<promptVersion>-<model-slug or "fallback">.md} */
	public static String fileName(LocalDate date, String promptVersion, Mode mode, String model) {
		String suffix = mode == Mode.MODEL ? slug(model) : "fallback";
		return date + "-" + promptVersion + "-" + suffix + ".md";
	}

	static String slug(String model) {
		String slug = model == null ? "" : model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		return slug.isEmpty() ? "unknown-model" : slug;
	}

	public static String render(LocalDate date, String promptVersion, Mode mode, String model, String baseUrl,
			EvalScores scores, List<CaseResult> results) {
		StringBuilder md = new StringBuilder();
		md.append("# Triage eval – ").append(promptVersion).append(" – ").append(date).append("\n\n");

		switch (mode) {
			case FALLBACK_NO_KEY -> md.append("""
					> **FALLBACK MODE – this is not a model evaluation.**
					> No `GROQ_API_KEY` was configured, so every case was answered by the rule-based client \
					(`rules-v1`) and no model was called. The numbers describe the safety-net rules only.
					> Thresholds are not enforced; the Pass column only shows what they would say.
					> Set `GROQ_API_KEY` and run again to evaluate the model.

					""");
			case FALLBACK_MODEL_UNAVAILABLE -> md.append("""
					> **FALLBACK MODE – the model never answered; this is not a model evaluation.**
					> A key is configured, but every call fell back to the rule-based client (`rules-v1`).
					> Check the key, the model name, Groq rate limits and the network, then run again.
					> Thresholds are not enforced; the Pass column only shows what they would say.

					""");
			case MODEL -> {
			}
		}

		if (mode == Mode.MODEL) {
			md.append("- Client: SpringAiTriageAiClient via ResilientTriageAiClient (model: ").append(model)
					.append(", base URL: ").append(baseUrl).append(")\n");
			int fellBack = scores.total() - scores.aiAnswered();
			if (fellBack > 0) {
				md.append("- Fallbacks: ").append(fellBack).append(" of ").append(scores.total())
						.append(" cases fell back to RuleBasedTriageAiClient (see Source below)\n");
			}
		}
		else {
			md.append("- Client: RuleBasedTriageAiClient via ResilientTriageAiClient (rules-v1, no model answered; ")
					.append("configured model: ").append(model).append(", base URL: ").append(baseUrl).append(")\n");
		}
		md.append("- Prompt version: ").append(promptVersion).append('\n');
		md.append("- Dataset: evals/triage/dataset.jsonl (").append(scores.total()).append(" cases)\n\n");

		boolean enforced = mode == Mode.MODEL;
		md.append("| Metric | Value | Pass").append(enforced ? "" : " (not enforced)").append(" |\n");
		md.append("|---|---|---|\n");
		row(md, "Urgent recall", fraction(scores.urgentHit(), scores.urgentTotal()), mark(scores.urgentRecallPasses()));
		row(md, "Priority accuracy", fraction(scores.correct(), scores.total()), mark(scores.accuracyPasses()));
		row(md, "Schema-valid rate", fraction(scores.aiAnswered(), scores.total()),
				enforced ? mark(scores.schemaValidPasses()) : "n/a (no model answered)");
		row(md, "Red-flag recall", fraction(scores.mentionHit(), scores.mentionTotal()),
				mark(scores.redFlagRecallPasses()));
		row(md, "Unsupported claims", Integer.toString(scores.unsupported()), mark(scores.unsupportedPasses()));
		row(md, "Latency p50 / p95", millis(scores.p50Millis()) + " / " + millis(scores.p95Millis()), "report only");

		md.append("\nThresholds: urgent recall 100%, accuracy ≥ 80%, schema-valid 100%, red-flag recall ≥ 80%, ")
				.append("unsupported claims 0.\n\n");

		md.append("## Per case\n");
		md.append("| Case | Expected | Got | Source | Red flags | Latency |\n");
		md.append("|---|---|---|---|---|---|\n");
		for (CaseResult result : results) {
			md.append("| ").append(result.evalCase().id())
					.append(" | ").append(result.evalCase().expectedPriority())
					.append(" | ").append(result.suggestion().suggestedPriority())
					.append(" | ").append(result.suggestion().source())
					.append(" | ").append(cell(String.join("; ", result.suggestion().redFlags())))
					.append(" | ").append(millis(result.latencyMillis()))
					.append(" |\n");
		}

		md.append("\n## Failures\n");
		if (scores.failures().isEmpty()) {
			md.append("- none\n");
		}
		else {
			scores.failures().forEach(failure -> md.append("- ").append(failure).append('\n'));
		}
		return md.toString();
	}

	private static void row(StringBuilder md, String metric, String value, String pass) {
		md.append("| ").append(metric).append(" | ").append(value).append(" | ").append(pass).append(" |\n");
	}

	private static String fraction(int part, int whole) {
		long percent = whole == 0 ? 100 : Math.round(100.0 * part / whole);
		return part + "/" + whole + " (" + percent + "%)";
	}

	private static String mark(boolean pass) {
		return pass ? "✅" : "❌";
	}

	private static String millis(double millis) {
		return String.format(Locale.ROOT, "%.1f ms", millis);
	}

	private static String cell(String text) {
		return text.isEmpty() ? "–" : text.replace("|", "\\|").replace("\n", " ");
	}
}
