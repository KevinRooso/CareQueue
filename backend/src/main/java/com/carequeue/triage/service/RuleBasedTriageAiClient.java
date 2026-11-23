package com.carequeue.triage.service;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic keyword triage, used when AI is not configured or the model fails. It is the safety
 * net, so it errs towards URGENT: the phrase lists are generic clinical red flags taken from the
 * prompt's own URGENT definition, not tuned to individual eval cases.
 *
 * <ul>
 *   <li>URGENT if any red-flag phrase matches and is not negated;</li>
 *   <li>else SOON if a "needs review" indicator matches;</li>
 *   <li>else ROUTINE if a "stable/mild" indicator matches;</li>
 *   <li>else SOON (unknown is not treated as routine).</li>
 * </ul>
 * A phrase is negated when "no", "not", "without" or "denies" comes before it in the same clause
 * ("No weight loss, no difficulty swallowing" is not urgent). Clauses end at punctuation and at
 * contrast words ("no fever but weight loss" is still urgent).
 */
public class RuleBasedTriageAiClient implements TriageAiClient {

	public static final String RULES_VERSION = "rules-v1";
	static final double CONFIDENCE = 0.5;
	static final int MAX_SUMMARY_WORDS = 40;

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final List<Pattern> URGENT = compile(
			"\\bweight loss\\b",
			"\\blos(?:t|ing) (?:\\w+ ){0,3}weight\\b",
			"\\ban(?:a)?emi(?:a|c)\\b",
			"\\brectal bleeding\\b",
			"\\bblood in (?:the )?stools?\\b",
			"\\bvomiting blood\\b",
			"\\bdifficulty (?:in )?swallowing\\b",
			"\\bdysphagia\\b",
			"\\bnew (?:\\w+ ){0,2}lump\\b",
			"\\bchest (?:pain|tightness|discomfort)\\b.{0,60}?\\b(?:exertion(?:al)?|exercise|walking|uphill|stairs)\\b",
			"\\b(?:exertion(?:al)?|exercise|walking|uphill|stairs)\\b.{0,60}?\\bchest (?:pain|tightness|discomfort)\\b",
			"\\b(?:mole|lesion)\\b.{0,60}?\\b(?:grown|growing|changed|changing|enlarged|enlarging)\\b",
			"\\b(?:growing|changing|enlarging) (?:\\w+ ){0,2}(?:mole|lesion)\\b",
			"\\brapidly (?:worsening|getting worse|deteriorating|progressing)\\b");

	private static final List<Pattern> SOON = compile(
			"\\bnot (?:settling|improving|responding)\\b",
			"\\bno improvement\\b",
			"\\bworsening\\b",
			"\\bgetting worse\\b",
			"\\bdespite\\b",
			"\\bpersistent\\b",
			"\\bmoderate\\b",
			"\\bendoscopy\\b",
			"\\bspecialist (?:review|opinion|assessment)\\b");

	private static final List<Pattern> ROUTINE = compile(
			"\\blong[- ]?standing\\b",
			"\\bstable\\b",
			"\\bmild\\b",
			"\\bcontrolled\\b",
			"\\broutine\\b",
			"\\breassurance\\b",
			"\\boccasional(?:ly)?\\b");

	private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+|\\R+");
	private static final Pattern CLAUSE_BREAK =
			Pattern.compile("[,;:]|\\b(?:but|however|although|though)\\b", FLAGS);
	private static final Pattern NEGATION = Pattern.compile("\\b(?:no|not|without|denies|denied)\\b", FLAGS);

	@Override
	public TriageSuggestion suggest(TriageInput input) {
		String notes = input.clinicalNotes().strip();
		List<String> sentences = Arrays.stream(SENTENCE_BREAK.split(notes))
				.map(String::strip)
				.filter(s -> !s.isEmpty())
				.toList();

		List<String> redFlags = findRedFlags(sentences);
		Priority priority;
		if (!redFlags.isEmpty()) {
			priority = Priority.URGENT;
		}
		else if (anyMatch(SOON, sentences)) {
			priority = Priority.SOON;
		}
		else if (anyMatch(ROUTINE, sentences)) {
			priority = Priority.ROUTINE;
		}
		else {
			priority = Priority.SOON;
		}

		return new TriageSuggestion(summary(sentences), priority, redFlags, CONFIDENCE, RULES_VERSION,
				SuggestionSource.RULE_BASED_FALLBACK);
	}

	private static List<String> findRedFlags(List<String> sentences) {
		// Keyed by lower case to drop duplicates; keeps the notes' own wording and order.
		Map<String, String> flags = new LinkedHashMap<>();
		for (String sentence : sentences) {
			List<int[]> spans = new ArrayList<>();
			for (Pattern pattern : URGENT) {
				Matcher m = pattern.matcher(sentence);
				while (m.find()) {
					if (!isNegated(sentence, m.start())) {
						spans.add(new int[] { m.start(), m.end() });
					}
				}
			}
			spans.sort((a, b) -> Integer.compare(a[0], b[0]));
			for (int[] span : spans) {
				String phrase = sentence.substring(span[0], span[1]);
				flags.putIfAbsent(phrase.toLowerCase(Locale.ROOT), phrase);
			}
		}
		return List.copyOf(flags.values());
	}

	private static boolean anyMatch(List<Pattern> patterns, List<String> sentences) {
		for (String sentence : sentences) {
			for (Pattern pattern : patterns) {
				Matcher m = pattern.matcher(sentence);
				while (m.find()) {
					if (!isNegated(sentence, m.start())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** True if a negation word appears between the start of the clause and the phrase. */
	private static boolean isNegated(String sentence, int phraseStart) {
		String before = sentence.substring(0, phraseStart);
		int clauseStart = 0;
		Matcher breaks = CLAUSE_BREAK.matcher(before);
		while (breaks.find()) {
			clauseStart = breaks.end();
		}
		return NEGATION.matcher(before.substring(clauseStart)).find();
	}

	private static String summary(List<String> sentences) {
		if (sentences.isEmpty()) {
			return "No clinical notes provided.";
		}
		String[] words = sentences.getFirst().split("\\s+");
		if (words.length <= MAX_SUMMARY_WORDS) {
			return sentences.getFirst();
		}
		return String.join(" ", Arrays.copyOf(words, MAX_SUMMARY_WORDS)) + " …";
	}

	private static List<Pattern> compile(String... regexes) {
		return Arrays.stream(regexes).map(r -> Pattern.compile(r, FLAGS)).toList();
	}
}
