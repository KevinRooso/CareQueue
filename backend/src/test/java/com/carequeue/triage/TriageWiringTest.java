package com.carequeue.triage;

import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.service.ResilientTriageAiClient;
import com.carequeue.triage.service.SpringAiTriageAiClient;
import com.carequeue.triage.service.TriageAiClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ClassUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that application.yml wires triage as designed, with the key explicitly blank so the result
 * never depends on the developer's environment. No network: AI is off, and nothing here calls the model.
 */
@SpringBootTest(classes = TriageSliceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "GROQ_API_KEY=", "spring.ai.openai.api-key=" })
class TriageWiringTest {

	@Autowired
	private TriageAiClient triageAiClient;

	@Autowired
	private SpringAiTriageAiClient springAiTriageAiClient;

	@Autowired
	private OpenAiChatModel openAiChatModel;

	@Autowired
	private OpenAiCommonProperties openAiProperties;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	void triageAiClient_whenNoKey_isResilientClientWithAiDisabled() {
		assertThat(triageAiClient).isInstanceOf(ResilientTriageAiClient.class);
		assertThat(((ResilientTriageAiClient) triageAiClient).isAiEnabled()).isFalse();
	}

	@Test
	void suggest_whenNoKey_goesStraightToRules() {
		var suggestion = triageAiClient.suggest(new TriageInput("DERMATOLOGY", 24,
				"Long-standing mild eczema, controlled with emollients."));

		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
		assertThat(meterRegistry.get("carequeue.triage.suggestions")
				.tag("source", "RULE_BASED_FALLBACK").tag("reason", "not_configured")
				.counter().count()).isGreaterThanOrEqualTo(1.0);
	}

	@Test
	void openAiChatModel_fromApplicationYml_usesGroqModelJsonModeAndTemperatureZero() {
		OpenAiChatOptions options = openAiChatModel.getOptions();

		assertThat(options.getModel()).isEqualTo("llama-3.3-70b-versatile");
		assertThat(options.getTemperature()).isEqualTo(0.0);
		assertThat(options.getResponseFormat()).isNotNull();
		assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
	}

	@Test
	void openAiClient_fromApplicationYml_pointsAtGroqWithSdkRetriesOffAndTimeout() {
		assertThat(openAiProperties.getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
		assertThat(openAiProperties.getMaxRetries()).isZero();
		assertThat(openAiProperties.getTimeout()).isEqualTo(Duration.ofSeconds(20));
		assertThat(openAiProperties.getApiKey()).isEmpty();
	}

	@Test
	void springAiTriageAiClient_inContext_isWrappedInRetryProxy() {
		assertThat(AopUtils.isAopProxy(springAiTriageAiClient)).isTrue();
	}

	@Test
	void springAiRetryModule_isNotOnClasspath_soOurRetryIsTheOnlyLayer() {
		// If this fails, spring-ai-retry was added: its RetryTemplate auto-configuration would become
		// active. Review the retry layering (ADR-007) before changing this test.
		assertThat(ClassUtils.isPresent("org.springframework.ai.retry.RetryUtils", null)).isFalse();
	}
}
