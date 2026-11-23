package com.carequeue.triage.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the triage clients.
 *
 * <p>{@code @EnableResilientMethods} turns on Spring Framework 7's {@code @Retryable}. With
 * {@code proxyTargetClass = true} the retry proxy is a subclass of {@link SpringAiTriageAiClient}, so it
 * can be injected by its class.
 *
 * <p>No-key mode: Spring AI 2.1 accepts an empty API key (it then builds a client that sends no
 * Authorization header), so the context starts without a key. We read the same property here and,
 * when it is blank, {@link ResilientTriageAiClient} never calls the model.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods(proxyTargetClass = true)
public class TriageConfig {

	private static final Logger log = LoggerFactory.getLogger(TriageConfig.class);

	@Bean
	TriagePromptRenderer triagePromptRenderer(ResourceLoader resourceLoader,
			@Value("${carequeue.triage.prompt-version}") String promptVersion) {
		return new TriagePromptRenderer(resourceLoader, promptVersion);
	}

	@Bean
	TriageReplyValidator triageReplyValidator() {
		return new TriageReplyValidator();
	}

	@Bean
	SpringAiTriageAiClient springAiTriageAiClient(ChatClient.Builder chatClientBuilder,
			TriagePromptRenderer promptRenderer, TriageReplyValidator validator, JsonMapper jsonMapper) {
		return new SpringAiTriageAiClient(chatClientBuilder, promptRenderer, validator, jsonMapper);
	}

	@Bean
	RuleBasedTriageAiClient ruleBasedTriageAiClient() {
		return new RuleBasedTriageAiClient();
	}

	@Bean
	@Primary
	ResilientTriageAiClient resilientTriageAiClient(SpringAiTriageAiClient springAiTriageAiClient,
			RuleBasedTriageAiClient ruleBasedTriageAiClient, MeterRegistry meterRegistry,
			@Value("${spring.ai.openai.api-key:}") String apiKey,
			@Value("${spring.ai.openai.chat.model:}") String model) {
		boolean aiEnabled = StringUtils.hasText(apiKey);
		if (!aiEnabled) {
			log.info("AI triage is off (no GROQ_API_KEY): suggestions come from the rule-based client");
		}
		else if (!StringUtils.hasText(model)) {
			log.warn("AI triage key is set but spring.ai.openai.chat.model is blank; check CAREQUEUE_TRIAGE_MODEL");
		}
		else {
			log.info("AI triage is on (model={})", model);
		}
		return new ResilientTriageAiClient(springAiTriageAiClient, ruleBasedTriageAiClient, meterRegistry, aiEnabled);
	}
}
