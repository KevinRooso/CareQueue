package com.carequeue.triage.service;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageReplyValidatorTest {

	private static final String SUMMARY = "Six weeks of looser stools with weight loss and anaemia.";
	private static final List<String> FLAGS = List.of("weight loss", "anaemia");

	private final TriageReplyValidator validator = new TriageReplyValidator();

	@Test
	void validate_whenReplyValid_returnsAiSuggestion() {
		TriageSuggestion suggestion = validator.validate(
				new ModelTriageReply("  " + SUMMARY + " ", "URGENT", List.of(" weight loss ", "anaemia"), 0.9),
				"triage-v1");

		assertThat(suggestion.summary()).isEqualTo(SUMMARY);
		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.URGENT);
		assertThat(suggestion.redFlags()).containsExactly("weight loss", "anaemia");
		assertThat(suggestion.confidence()).isEqualTo(0.9);
		assertThat(suggestion.promptVersion()).isEqualTo("triage-v1");
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
	}

	@Test
	void validate_whenBoundaryValues_accepts() {
		String eightyWords = String.join(" ", Collections.nCopies(TriageReplyValidator.MAX_SUMMARY_WORDS, "word"));
		List<String> tenFlags = Collections.nCopies(TriageReplyValidator.MAX_RED_FLAGS,
				"x".repeat(TriageReplyValidator.MAX_RED_FLAG_LENGTH));

		assertThat(validator.validate(new ModelTriageReply(eightyWords, "ROUTINE", tenFlags, 0.0), "triage-v1")
				.suggestedPriority()).isEqualTo(Priority.ROUTINE);
		assertThat(validator.validate(new ModelTriageReply(SUMMARY, "SOON", List.of(), 1.0), "triage-v1")
				.redFlags()).isEmpty();
	}

	static Stream<Arguments> invalidReplies() {
		String eightyOneWords = String.join(" ", Collections.nCopies(TriageReplyValidator.MAX_SUMMARY_WORDS + 1, "word"));
		return Stream.of(
				Arguments.of("summary null", new ModelTriageReply(null, "URGENT", FLAGS, 0.9)),
				Arguments.of("summary blank", new ModelTriageReply("  ", "URGENT", FLAGS, 0.9)),
				Arguments.of("summary over 80 words", new ModelTriageReply(eightyOneWords, "URGENT", FLAGS, 0.9)),
				Arguments.of("priority null", new ModelTriageReply(SUMMARY, null, FLAGS, 0.9)),
				Arguments.of("priority lower case", new ModelTriageReply(SUMMARY, "urgent", FLAGS, 0.9)),
				Arguments.of("priority unknown", new ModelTriageReply(SUMMARY, "HIGH", FLAGS, 0.9)),
				Arguments.of("redFlags null", new ModelTriageReply(SUMMARY, "URGENT", null, 0.9)),
				Arguments.of("redFlags over 10", new ModelTriageReply(SUMMARY, "URGENT",
						Collections.nCopies(TriageReplyValidator.MAX_RED_FLAGS + 1, "flag"), 0.9)),
				Arguments.of("redFlag blank", new ModelTriageReply(SUMMARY, "URGENT", List.of("weight loss", " "), 0.9)),
				Arguments.of("redFlag null", new ModelTriageReply(SUMMARY, "URGENT", Arrays.asList("weight loss", null), 0.9)),
				Arguments.of("redFlag over 120 chars", new ModelTriageReply(SUMMARY, "URGENT",
						List.of("x".repeat(TriageReplyValidator.MAX_RED_FLAG_LENGTH + 1)), 0.9)),
				Arguments.of("confidence null", new ModelTriageReply(SUMMARY, "URGENT", FLAGS, null)),
				Arguments.of("confidence below 0", new ModelTriageReply(SUMMARY, "URGENT", FLAGS, -0.01)),
				Arguments.of("confidence above 1", new ModelTriageReply(SUMMARY, "URGENT", FLAGS, 1.01)),
				Arguments.of("confidence NaN", new ModelTriageReply(SUMMARY, "URGENT", FLAGS, Double.NaN)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidReplies")
	void validate_whenRuleBroken_throwsInvalidModelOutput(String rule, ModelTriageReply reply) {
		assertThatThrownBy(() -> validator.validate(reply, "triage-v1"))
				.isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void validate_whenReplyNull_throwsInvalidModelOutput() {
		assertThatThrownBy(() -> validator.validate(null, "triage-v1"))
				.isInstanceOf(InvalidModelOutputException.class);
	}
}
