package com.carequeue.triage.service;

import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real {@link TriageConfig} in a small Spring context, so the real {@code @Retryable} proxy and
 * the real retry settings are exercised. Only the model is stubbed ({@link StubChatModel}): no network.
 */
@ExtendWith(OutputCaptureExtension.class)
class ResilientTriageAiClientTest {

	private static final int MAX_RETRIES = 2;

	private static final TriageInput INPUT = new TriageInput("GASTRO", 58,
			"Synthetic notes: change in bowel habit with 5kg unintentional weight loss.");

	private static final String VALID_JSON = """
			{"summary": "Change in bowel habit with weight loss.", "suggestedPriority": "URGENT",
			 "redFlags": ["weight loss"], "confidence": 0.8}""";

	private static void run(StubChatModel model, String apiKey, Consumer<ApplicationContext> assertions) {
		new ApplicationContextRunner()
				.withUserConfiguration(TriageConfig.class)
				.withBean(ChatClient.Builder.class, () -> ChatClient.builder(model))
				.withBean(JsonMapper.class, () -> JsonMapper.builder().build())
				.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
				.withPropertyValues(
						"carequeue.triage.prompt-version=triage-v1",
						"carequeue.triage.retry.max-retries=" + MAX_RETRIES,
						"carequeue.triage.retry.delay=1ms",
						"spring.ai.openai.chat.model=stub-model",
						"spring.ai.openai.api-key=" + apiKey)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertions.accept(context);
				});
	}

	private static TriageSuggestion suggest(ApplicationContext context) {
		return context.getBean(TriageAiClient.class).suggest(INPUT);
	}

	private static double count(ApplicationContext context, String source, String reason) {
		return context.getBean(MeterRegistry.class)
				.get(ResilientTriageAiClient.METRIC)
				.tag("source", source)
				.tag("reason", reason)
				.counter()
				.count();
	}

	@Test
	void suggest_whenNotConfigured_usesRulesAndNeverCallsModel() {
		StubChatModel model = StubChatModel.of(VALID_JSON);

		run(model, "", context -> {
			assertThat(context.getBean(TriageAiClient.class)).isInstanceOf(ResilientTriageAiClient.class);

			TriageSuggestion suggestion = suggest(context);

			assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
			assertThat(model.calls()).isZero();
			assertThat(count(context, "RULE_BASED_FALLBACK", "not_configured")).isEqualTo(1.0);
			assertThat(count(context, "AI", "ok")).isZero();
		});
	}

	@Test
	void suggest_whenKeyIsWhitespace_treatsAiAsNotConfigured() {
		StubChatModel model = StubChatModel.of(VALID_JSON);

		run(model, "   ", context -> {
			assertThat(suggest(context).source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
			assertThat(model.calls()).isZero();
		});
	}

	@Test
	void suggest_whenAiAnswersValidJson_returnsAiSuggestion() {
		StubChatModel model = StubChatModel.of(VALID_JSON);

		run(model, "test-key-not-real", context -> {
			TriageSuggestion suggestion = suggest(context);

			assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
			assertThat(suggestion.promptVersion()).isEqualTo("triage-v1");
			assertThat(model.calls()).isEqualTo(1);
			assertThat(count(context, "AI", "ok")).isEqualTo(1.0);
		});
	}

	static Stream<Arguments> transientFailures() {
		return Stream.of(
				Arguments.of("timeout / IO", OpenAiErrors.timeout()),
				Arguments.of("HTTP 429", OpenAiErrors.rateLimited()),
				Arguments.of("HTTP 503", OpenAiErrors.serverError(503)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("transientFailures")
	void suggest_whenTransientFailureEveryTime_retriesThenFallsBack(String name, RuntimeException failure) {
		StubChatModel model = StubChatModel.of(failure);

		run(model, "test-key-not-real", context -> {
			TriageSuggestion suggestion = suggest(context);

			assertThat(model.calls()).isEqualTo(1 + MAX_RETRIES);
			assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
			assertThat(count(context, "RULE_BASED_FALLBACK", "error")).isEqualTo(1.0);
		});
	}

	@Test
	void suggest_whenTransientFailureOnceThenSuccess_returnsAiResult() {
		StubChatModel model = StubChatModel.of(OpenAiErrors.rateLimited(), VALID_JSON);

		run(model, "test-key-not-real", context -> {
			TriageSuggestion suggestion = suggest(context);

			assertThat(model.calls()).isEqualTo(2);
			assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
			assertThat(count(context, "AI", "ok")).isEqualTo(1.0);
			assertThat(count(context, "RULE_BASED_FALLBACK", "error")).isZero();
		});
	}

	@Test
	void suggest_whenModelOutputInvalid_callsOnceThenFallsBack() {
		StubChatModel model = StubChatModel.of("Sorry, I cannot help with that.");

		run(model, "test-key-not-real", context -> {
			TriageSuggestion suggestion = suggest(context);

			assertThat(model.calls()).isEqualTo(1);
			assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
			assertThat(count(context, "RULE_BASED_FALLBACK", "invalid_output")).isEqualTo(1.0);
		});
	}

	@Test
	void suggest_whenNonTransientHttpError_callsOnceThenFallsBack() {
		StubChatModel model = StubChatModel.of(OpenAiErrors.unauthorized());

		run(model, "test-key-not-real", context -> {
			TriageSuggestion suggestion = suggest(context);

			assertThat(model.calls()).isEqualTo(1);
			assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
			assertThat(count(context, "RULE_BASED_FALLBACK", "error")).isEqualTo(1.0);
		});
	}

	@Test
	void suggest_whenFallingBack_logsReasonClassOnly(CapturedOutput output) {
		StubChatModel model = StubChatModel.of("{\"summary\": \"synthetic reply text");

		run(model, "test-key-not-real", context -> suggest(context));

		assertThat(output.getAll())
				.contains("reason=InvalidModelOutputException")
				.doesNotContain(INPUT.clinicalNotes())
				.doesNotContain("synthetic reply text");
	}

	@Test
	void springAiTriageAiClient_inContext_isWrappedInRetryProxy() {
		run(StubChatModel.of(VALID_JSON), "test-key-not-real", context ->
				assertThat(AopUtils.isAopProxy(context.getBean(SpringAiTriageAiClient.class))).isTrue());
	}
}
