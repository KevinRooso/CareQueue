package com.carequeue;

import com.carequeue.triage.service.ResilientTriageAiClient;
import com.carequeue.triage.service.TriageAiClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// The AI key is forced blank so the result never depends on the developer's environment or .env:
// the whole app must start without a key.
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@SpringBootTest(properties = { "GROQ_API_KEY=", "spring.ai.openai.api-key=" })
class CareQueueApplicationTests {

	@Autowired
	private TriageAiClient triageAiClient;

	@Test
	void contextLoads() {
	}

	@Test
	void triageAiClient_whenNoKey_isResilientClientWithAiDisabled() {
		assertThat(triageAiClient).isInstanceOf(ResilientTriageAiClient.class);
		assertThat(((ResilientTriageAiClient) triageAiClient).isAiEnabled()).isFalse();
	}
}
