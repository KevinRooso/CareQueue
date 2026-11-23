package com.carequeue.triage.eval;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The metrics from evals/README.md, computed from the case results.
 *
 * @param failures one line per case that missed something, e.g. {@code T07: expected SOON, got URGENT}
 */
public record EvalScores(
		int total,
		int urgentTotal,
		int urgentHit,
		int correct,
		int aiAnswered,
		int mentionTotal,
		int mentionHit,
		int unsupported,
		double p50Millis,
		double p95Millis,
		List<String> failures) {

	public static final double MIN_ACCURACY = 0.8;
	public static final double MIN_RED_FLAG_RECALL = 0.8;

	public static EvalScores from(List<CaseResult> results) {
		int urgentTotal = 0;
		int urgentHit = 0;
		int correct = 0;
		int aiAnswered = 0;
		int mentionTotal = 0;
		int mentionHit = 0;
		int unsupported = 0;
		List<String> failures = new ArrayList<>();

		for (CaseResult result : results) {
			EvalCase evalCase = result.evalCase();
			Priority expected = evalCase.expectedPriority();
			Priority got = result.suggestion().suggestedPriority();
			String text = result.searchableText();
			List<String> problems = new ArrayList<>();

			if (expected == Priority.URGENT) {
				urgentTotal++;
				if (got == Priority.URGENT) {
					urgentHit++;
				}
			}
			if (got == expected) {
				correct++;
			}
			else {
				problems.add("expected " + expected + ", got " + got);
			}
			if (result.suggestion().source() == SuggestionSource.AI) {
				aiAnswered++;
			}
			for (String item : evalCase.mustMention()) {
				mentionTotal++;
				if (mentions(text, item)) {
					mentionHit++;
				}
				else {
					problems.add("missing \"" + item + "\"");
				}
			}
			for (String item : evalCase.mustNotMention()) {
				if (mentions(text, item)) {
					unsupported++;
					problems.add("unsupported \"" + item + "\"");
				}
			}
			if (!problems.isEmpty()) {
				failures.add(evalCase.id() + ": " + String.join("; ", problems)
						+ " (source " + result.suggestion().source() + ")");
			}
		}

		double[] latencies = results.stream().mapToDouble(CaseResult::latencyMillis).sorted().toArray();
		return new EvalScores(results.size(), urgentTotal, urgentHit, correct, aiAnswered, mentionTotal, mentionHit,
				unsupported, percentile(latencies, 0.50), percentile(latencies, 0.95), List.copyOf(failures));
	}

	/** Case-insensitive substring match; {@code "a|b"} means either alternative is fine. */
	static boolean mentions(String text, String item) {
		String haystack = text.toLowerCase(Locale.ROOT);
		return Arrays.stream(item.split("\\|"))
				.map(alt -> alt.strip().toLowerCase(Locale.ROOT))
				.filter(alt -> !alt.isEmpty())
				.anyMatch(haystack::contains);
	}

	/** Nearest-rank percentile of already sorted values. */
	static double percentile(double[] sorted, double quantile) {
		if (sorted.length == 0) {
			return 0.0;
		}
		int rank = (int) Math.ceil(quantile * sorted.length);
		return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
	}

	public double urgentRecall() {
		return ratio(urgentHit, urgentTotal);
	}

	public double accuracy() {
		return ratio(correct, total);
	}

	public double schemaValidRate() {
		return ratio(aiAnswered, total);
	}

	public double redFlagRecall() {
		return ratio(mentionHit, mentionTotal);
	}

	public boolean urgentRecallPasses() {
		return urgentHit == urgentTotal;
	}

	public boolean accuracyPasses() {
		return accuracy() >= MIN_ACCURACY;
	}

	public boolean schemaValidPasses() {
		return aiAnswered == total;
	}

	public boolean redFlagRecallPasses() {
		return redFlagRecall() >= MIN_RED_FLAG_RECALL;
	}

	public boolean unsupportedPasses() {
		return unsupported == 0;
	}

	private static double ratio(int part, int whole) {
		return whole == 0 ? 1.0 : (double) part / whole;
	}
}
