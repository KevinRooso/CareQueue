package com.carequeue.triage.eval;

import com.carequeue.referral.domain.Priority;
import com.carequeue.triage.domain.TriageInput;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One line of {@code evals/triage/dataset.jsonl} (see evals/README.md). All cases are synthetic. */
public record EvalCase(
		String id,
		String clinic,
		int age,
		String clinicalNotes,
		Priority expectedPriority,
		List<String> mustMention,
		List<String> mustNotMention) {

	/** Tests run with backend/ as the working directory, so the dataset is one level up. */
	public static final Path DATASET = Path.of("..", "evals", "triage", "dataset.jsonl");

	public EvalCase {
		mustMention = mustMention == null ? List.of() : List.copyOf(mustMention);
		mustNotMention = mustNotMention == null ? List.of() : List.copyOf(mustNotMention);
	}

	public TriageInput toInput() {
		return new TriageInput(clinic, age, clinicalNotes);
	}

	public static List<EvalCase> loadDataset() {
		return loadDataset(JsonMapper.builder().build());
	}

	public static List<EvalCase> loadDataset(JsonMapper jsonMapper) {
		try {
			return Files.readAllLines(DATASET, StandardCharsets.UTF_8).stream()
					.filter(line -> !line.isBlank())
					.map(line -> jsonMapper.readValue(line, EvalCase.class))
					.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read " + DATASET.toAbsolutePath().normalize(), ex);
		}
	}

	@Override
	public String toString() {
		return id + " (" + clinic + ", expected " + expectedPriority + ")";
	}
}
