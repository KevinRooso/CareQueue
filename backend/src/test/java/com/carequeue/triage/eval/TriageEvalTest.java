package com.carequeue.triage.eval;

import com.carequeue.triage.TriageSliceConfig;
import com.carequeue.triage.domain.TriageSuggestion;
import com.carequeue.triage.service.ResilientTriageAiClient;
import com.carequeue.triage.service.TriageAiClient;
import com.carequeue.triage.service.TriagePromptRenderer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The AI triage eval (evals/README.md). Skipped in normal builds; run with
 * {@code cd backend && ./mvnw -q test -Dgroups=eval}.
 *
 * <p>The only test allowed to call the real model: it opts back in to {@code GROQ_API_KEY} (from the
 * environment or the repo-root .env), which the test-only config/application.yml blanks for all other tests.
 * It calls the {@code @Primary} {@link TriageAiClient} for every dataset case and writes
 * {@code evals/results/<date>-<promptVersion>-<model-slug | fallback>.md}.
 * Thresholds are asserted only when a real model answered; without a key it writes a FALLBACK MODE
 * report and passes.
 */
@Tag("eval")
@SpringBootTest(classes = TriageSliceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = "spring.ai.openai.api-key=${GROQ_API_KEY:}")
class TriageEvalTest {

	private static final Logger log = LoggerFactory.getLogger(TriageEvalTest.class);
	private static final Path RESULTS = Path.of("..", "evals", "results");

	@Autowired
	private TriageAiClient triageAiClient;

	@Autowired
	private ResilientTriageAiClient resilientTriageAiClient;

	@Autowired
	private TriagePromptRenderer promptRenderer;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private Environment environment;

	@Test
	void evaluate_overDataset_writesReportAndEnforcesThresholdsForRealModel() throws IOException {
		List<CaseResult> results = new ArrayList<>();
		for (EvalCase evalCase : EvalCase.loadDataset(jsonMapper)) {
			long start = System.nanoTime();
			TriageSuggestion suggestion = triageAiClient.suggest(evalCase.toInput());
			results.add(new CaseResult(evalCase, suggestion, System.nanoTime() - start));
		}

		EvalScores scores = EvalScores.from(results);
		EvalReport.Mode mode = EvalReport.mode(resilientTriageAiClient.isAiEnabled(), scores);
		String model = environment.getProperty("spring.ai.openai.chat.model", "");
		String baseUrl = environment.getProperty("spring.ai.openai.base-url", "");
		LocalDate today = LocalDate.now();

		Path report = RESULTS.resolve(EvalReport.fileName(today, promptRenderer.version(), mode, model));
		Files.createDirectories(RESULTS);
		Files.writeString(report,
				EvalReport.render(today, promptRenderer.version(), mode, model, baseUrl, scores, results),
				StandardCharsets.UTF_8);
		log.info("Triage eval ({}) report: {}", mode, report.toAbsolutePath().normalize());

		if (mode != EvalReport.Mode.MODEL) {
			log.warn("Triage eval ran in {}: thresholds not enforced, this is not a model evaluation", mode);
			return;
		}
		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(scores.urgentHit()).as("urgent recall (must be 100%%)").isEqualTo(scores.urgentTotal());
		softly.assertThat(scores.accuracy()).as("priority accuracy").isGreaterThanOrEqualTo(EvalScores.MIN_ACCURACY);
		softly.assertThat(scores.aiAnswered()).as("schema-valid responses").isEqualTo(scores.total());
		softly.assertThat(scores.redFlagRecall()).as("red-flag recall")
				.isGreaterThanOrEqualTo(EvalScores.MIN_RED_FLAG_RECALL);
		softly.assertThat(scores.unsupported()).as("unsupported claims").isZero();
		softly.assertAll();
	}
}
