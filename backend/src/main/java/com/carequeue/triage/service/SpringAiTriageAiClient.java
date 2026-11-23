package com.carequeue.triage.service;

import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.resilience.annotation.Retryable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the model (Groq through Spring AI's OpenAI-compatible client, ADR-007) for a triage suggestion.
 *
 * <p>Flow: render the versioned prompt, send it with {@link ChatClient}, take the raw text, strip an
 * optional markdown code fence, parse it into {@link ModelTriageReply} with the app's Jackson
 * {@link JsonMapper}, then validate it into a {@link TriageSuggestion}. A reply that is not usable
 * throws {@link InvalidModelOutputException}.
 *
 * <p>Retries (Spring Framework 7 {@code @Retryable}) cover transient failures only. These are the
 * exception types the OpenAI Java SDK behind Spring AI 2.1 throws for them:
 * <ul>
 *   <li>{@link OpenAIIoException}: network/IO failure, including connect, read and call timeouts</li>
 *   <li>{@link RateLimitException}: HTTP 429 (for example Groq free-tier rate limits)</li>
 *   <li>{@link InternalServerException}: any HTTP 5xx</li>
 * </ul>
 * Everything else (400, 401, 403, 404, 422, invalid output) is not retried. The SDK's own retries are
 * switched off ({@code spring.ai.openai.max-retries: 0}), so this is the only retry layer.
 * The retry only works when the call goes through the Spring proxy, which is why
 * {@link ResilientTriageAiClient} gets this class injected as a bean and never calls {@code new}.
 */
public class SpringAiTriageAiClient implements TriageAiClient {

	private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```[A-Za-z]*\\s*(.*?)\\s*```\\s*$", Pattern.DOTALL);

	private final ChatClient chatClient;
	private final TriagePromptRenderer promptRenderer;
	private final TriageReplyValidator validator;
	private final JsonMapper jsonMapper;

	public SpringAiTriageAiClient(ChatClient.Builder chatClientBuilder, TriagePromptRenderer promptRenderer,
			TriageReplyValidator validator, JsonMapper jsonMapper) {
		this.chatClient = chatClientBuilder.build();
		this.promptRenderer = promptRenderer;
		this.validator = validator;
		this.jsonMapper = jsonMapper;
	}

	@Override
	@Retryable(
			includes = { OpenAIIoException.class, RateLimitException.class, InternalServerException.class },
			excludes = InvalidModelOutputException.class,
			maxRetriesString = "${carequeue.triage.retry.max-retries:2}",
			delayString = "${carequeue.triage.retry.delay:500ms}",
			multiplierString = "${carequeue.triage.retry.multiplier:2}")
	public TriageSuggestion suggest(TriageInput input) {
		String prompt = promptRenderer.render(input);
		// No template parameters are passed, so ChatClient sends the text as-is (the JSON example's
		// braces are not treated as template variables).
		String reply = chatClient.prompt().user(prompt).call().content();
		return validator.validate(parse(reply), promptRenderer.version());
	}

	private ModelTriageReply parse(String reply) {
		if (reply == null || reply.isBlank()) {
			throw new InvalidModelOutputException("reply is empty");
		}
		String json = stripCodeFence(reply);
		try {
			return jsonMapper.readValue(json, ModelTriageReply.class);
		}
		catch (JacksonException ex) {
			// The cause is not attached: Jackson messages can quote the reply, which can echo clinical notes.
			throw new InvalidModelOutputException("reply is not valid JSON for the triage schema ("
					+ ex.getClass().getSimpleName() + ")");
		}
	}

	static String stripCodeFence(String reply) {
		Matcher matcher = CODE_FENCE.matcher(reply);
		return matcher.matches() ? matcher.group(1) : reply.strip();
	}
}
