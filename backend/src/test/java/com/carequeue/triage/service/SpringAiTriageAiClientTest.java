package com.carequeue.triage.service;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: no Spring context and no proxy, so no retries here (see ResilientTriageAiClientTest). */
class SpringAiTriageAiClientTest {

	private static final TriageInput INPUT = new TriageInput("GASTRO", 58,
			"Synthetic notes: 6 weeks of looser stools, 5kg weight loss, iron deficiency anaemia.");

	private static final String VALID_JSON = """
			{"summary": "Six weeks of looser stools with weight loss and anaemia.",
			 "suggestedPriority": "URGENT",
			 "redFlags": ["weight loss", "anaemia"],
			 "confidence": 0.86}""";

	private static SpringAiTriageAiClient clientReplying(StubChatModel model) {
		return new SpringAiTriageAiClient(ChatClient.builder(model),
				new TriagePromptRenderer(new DefaultResourceLoader(), "triage-v1"),
				new TriageReplyValidator(), JsonMapper.builder().build());
	}

	@Test
	void suggest_whenReplyIsValidJson_returnsAiSuggestionWithPromptVersion() {
		TriageSuggestion suggestion = clientReplying(StubChatModel.of(VALID_JSON)).suggest(INPUT);

		assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
		assertThat(suggestion.promptVersion()).isEqualTo("triage-v1");
		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.URGENT);
		assertThat(suggestion.redFlags()).containsExactly("weight loss", "anaemia");
		assertThat(suggestion.confidence()).isEqualTo(0.86);
	}

	@ParameterizedTest
	@ValueSource(strings = { "```json\n%s\n```", "```\n%s\n```", "  ```JSON %s```  \n" })
	void suggest_whenReplyIsFencedJson_parsesIt(String fence) {
		TriageSuggestion suggestion = clientReplying(StubChatModel.of(fence.formatted(VALID_JSON))).suggest(INPUT);

		assertThat(suggestion.suggestedPriority()).isEqualTo(Priority.URGENT);
	}

	@Test
	void suggest_whenReplyHasUnknownFields_ignoresThem() {
		String json = """
				{"summary": "Stable mild eczema.", "suggestedPriority": "ROUTINE", "redFlags": [],
				 "confidence": 0.7, "reasoning": "extra field the model added"}""";

		assertThat(clientReplying(StubChatModel.of(json)).suggest(INPUT).suggestedPriority())
				.isEqualTo(Priority.ROUTINE);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"I think this referral is urgent.",
			"{\"summary\": \"cut off",
			"[\"URGENT\"]",
			"{\"summary\": \"ok\", \"suggestedPriority\": \"URGENT\", \"redFlags\": \"weight loss\", \"confidence\": 0.9}",
			"null",
			"   " })
	void suggest_whenReplyIsNotTriageJson_throwsInvalidModelOutput(String reply) {
		SpringAiTriageAiClient client = clientReplying(StubChatModel.of(reply));

		assertThatThrownBy(() -> client.suggest(INPUT)).isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void suggest_whenJsonBreaksSchema_throwsInvalidModelOutput() {
		String json = """
				{"summary": "Weight loss.", "suggestedPriority": "HIGH", "redFlags": [], "confidence": 0.9}""";
		SpringAiTriageAiClient client = clientReplying(StubChatModel.of(json));

		assertThatThrownBy(() -> client.suggest(INPUT))
				.isInstanceOf(InvalidModelOutputException.class)
				.hasMessageContaining("suggestedPriority");
	}

	@Test
	void suggest_whenInvalid_exceptionMessageDoesNotEchoTheReply() {
		SpringAiTriageAiClient client = clientReplying(StubChatModel.of("{\"summary\": \"synthetic secret text"));

		assertThatThrownBy(() -> client.suggest(INPUT))
				.isInstanceOf(InvalidModelOutputException.class)
				.hasNoCause()
				.message().doesNotContain("synthetic secret text");
	}

	@Test
	void suggest_whenCalled_sendsRenderedPromptAsIs() {
		StubChatModel model = StubChatModel.of(VALID_JSON);

		clientReplying(model).suggest(INPUT);

		String sent = model.lastPrompt().getContents();
		assertThat(sent)
				.contains("Clinic: GASTRO")
				.contains("Patient age: 58")
				.contains(INPUT.clinicalNotes())
				.contains("{\"summary\": string,");
	}
}
