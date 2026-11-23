package com.carequeue.triage;

import com.carequeue.referral.domain.SuggestionSource;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import com.carequeue.triage.service.TriageAiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real Spring AI 2.1 OpenAI client (OpenAI Java SDK + OkHttp) against a fake of the OpenAI-compatible
 * chat completions endpoint running on 127.0.0.1 inside this JVM. Nothing leaves the machine.
 *
 * <p>Proves what the unit tests can only assume: JSON mode, temperature and model really go on the wire;
 * HTTP 429/5xx and timeouts surface as the exception types we retry; and there is exactly one retry layer
 * (with the SDK's own retries on, a failing call would hit the server 3 x 4 times instead of 3).
 */
@SpringBootTest(classes = TriageSliceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.ai.openai.api-key=test-key-not-real",
				"spring.ai.openai.timeout=500ms",
				"carequeue.triage.retry.max-retries=2",
				"carequeue.triage.retry.delay=1ms" })
class TriageHttpContractTest {

	private static final FakeChatCompletions SERVER = FakeChatCompletions.start();

	private static final TriageInput INPUT = new TriageInput("GASTRO", 67,
			"Synthetic notes: new difficulty swallowing solid foods over 4 weeks.");

	private static final String MODEL_JSON = """
			{"summary": "New difficulty swallowing solids for 4 weeks.", "suggestedPriority": "URGENT", \
			"redFlags": ["difficulty swallowing"], "confidence": 0.9}""";

	@Autowired
	private TriageAiClient triageAiClient;

	@DynamicPropertySource
	static void fakeServer(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.openai.base-url", SERVER::baseUrl);
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop();
	}

	@BeforeEach
	void resetServer() {
		SERVER.reset();
	}

	@Test
	void suggest_whenServerAnswers_sendsJsonModeTemperatureAndModelAndParsesReply() throws Exception {
		SERVER.script(Reply.ok(MODEL_JSON));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
		assertThat(SERVER.requests()).isEqualTo(1);
		assertThat(SERVER.lastPath()).isEqualTo("/openai/v1/chat/completions");
		assertThat(SERVER.lastAuthorization()).isEqualTo("Bearer test-key-not-real");
		JsonNode body = JsonMapper.builder().build().readTree(SERVER.lastBody());
		assertThat(body.path("model").asString()).isEqualTo("llama-3.3-70b-versatile");
		assertThat(body.path("temperature").asDouble()).isEqualTo(0.0);
		assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_object");
		assertThat(body.path("messages").get(0).path("content").asString()).contains(INPUT.clinicalNotes());
	}

	@Test
	void suggest_whenRateLimitedEveryTime_hitsServerOncePerOurAttemptOnly() {
		SERVER.script(Reply.status(429));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(SERVER.requests()).isEqualTo(3);
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
	}

	@Test
	void suggest_whenServerErrorOnceThenOk_retriesAndReturnsAiResult() {
		SERVER.script(Reply.status(503), Reply.ok(MODEL_JSON));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(SERVER.requests()).isEqualTo(2);
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.AI);
	}

	@Test
	void suggest_whenServerTooSlow_timesOutRetriesThenFallsBack() {
		SERVER.script(Reply.slow(1_500));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(SERVER.requests()).isEqualTo(3);
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
	}

	@Test
	void suggest_whenUnauthorized_doesNotRetry() {
		SERVER.script(Reply.status(401));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(SERVER.requests()).isEqualTo(1);
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
	}

	@Test
	void suggest_whenModelRepliesWithProse_doesNotRetry() {
		SERVER.script(Reply.ok("Here is my assessment: urgent."));

		TriageSuggestion suggestion = triageAiClient.suggest(INPUT);

		assertThat(SERVER.requests()).isEqualTo(1);
		assertThat(suggestion.source()).isEqualTo(SuggestionSource.RULE_BASED_FALLBACK);
	}

	/** One scripted answer of the fake server. */
	private record Reply(int status, String content, long delayMillis) {

		static Reply ok(String content) {
			return new Reply(200, content, 0);
		}

		static Reply status(int status) {
			return new Reply(status, null, 0);
		}

		static Reply slow(long delayMillis) {
			return new Reply(200, MODEL_JSON, delayMillis);
		}
	}

	/** A tiny OpenAI-compatible {@code POST /openai/v1/chat/completions} on a loopback port. */
	private static final class FakeChatCompletions {

		private final HttpServer server;
		private final List<Reply> script = new CopyOnWriteArrayList<>();
		private final AtomicInteger requests = new AtomicInteger();
		private volatile String lastPath;
		private volatile String lastAuthorization;
		private volatile String lastBody;

		private FakeChatCompletions(HttpServer server) {
			this.server = server;
		}

		static FakeChatCompletions start() {
			try {
				HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
				FakeChatCompletions fake = new FakeChatCompletions(server);
				server.createContext("/", fake::handle);
				server.setExecutor(Executors.newCachedThreadPool());
				server.start();
				return fake;
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
		}

		void script(Reply... replies) {
			script.clear();
			script.addAll(List.of(replies));
		}

		void reset() {
			script.clear();
			requests.set(0);
		}

		void stop() {
			server.stop(0);
		}

		int requests() {
			return requests.get();
		}

		String lastPath() {
			return lastPath;
		}

		String lastAuthorization() {
			return lastAuthorization;
		}

		String lastBody() {
			return lastBody;
		}

		private void handle(HttpExchange exchange) throws IOException {
			int index = requests.getAndIncrement();
			lastPath = exchange.getRequestURI().getPath();
			lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Reply reply = script.isEmpty() ? Reply.status(500) : script.get(Math.min(index, script.size() - 1));
			if (reply.delayMillis() > 0) {
				try {
					Thread.sleep(reply.delayMillis());
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			String body = reply.status() == 200 ? completion(reply.content())
					: "{\"error\":{\"message\":\"synthetic error\",\"type\":\"test\",\"code\":\"test\"}}";
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			try {
				exchange.sendResponseHeaders(reply.status(), bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (IOException ignored) {
				// The client gave up (timeout test); nothing to do.
			}
			finally {
				exchange.close();
			}
		}

		private static String completion(String content) {
			String escaped = JsonMapper.builder().build().writeValueAsString(content);
			return """
					{"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000,
					 "model": "llama-3.3-70b-versatile",
					 "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
					   "message": {"role": "assistant", "content": %s, "refusal": null}}],
					 "usage": {"prompt_tokens": 10, "completion_tokens": 10, "total_tokens": 20}}
					""".formatted(escaped);
		}
	}
}
