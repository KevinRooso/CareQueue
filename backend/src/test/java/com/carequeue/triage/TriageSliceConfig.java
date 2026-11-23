package com.carequeue.triage;

import com.carequeue.triage.service.TriageConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Just the triage part of the app: the real application.yml, Spring AI's OpenAI chat auto-configuration,
 * Jackson and {@link TriageConfig}. No database, web or security, so it starts in about a second and
 * needs no Docker. Use it as {@code @SpringBootTest(classes = TriageSliceConfig.class)}.
 *
 * <p>Deliberately not a {@code @Configuration}: the full-app tests scan {@code com.carequeue} (test classes
 * included) and must not pick this class up.
 */
@ImportAutoConfiguration({
		JacksonAutoConfiguration.class,
		ToolCallingAutoConfiguration.class,
		OpenAiChatAutoConfiguration.class,
		ChatClientAutoConfiguration.class })
@Import(TriageConfig.class)
public class TriageSliceConfig {

	@Bean
	MeterRegistry meterRegistry() {
		return new SimpleMeterRegistry();
	}
}
