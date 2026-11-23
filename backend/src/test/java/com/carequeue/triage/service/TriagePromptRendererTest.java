package com.carequeue.triage.service;

import com.carequeue.triage.domain.TriageInput;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriagePromptRendererTest {

	private final TriagePromptRenderer renderer = new TriagePromptRenderer(new DefaultResourceLoader(), "triage-v1");

	@Test
	void render_whenCalled_replacesAllPlaceholders() {
		String prompt = renderer.render(new TriageInput("GASTRO", 58, "Synthetic notes: six weeks of bloating."));

		assertThat(prompt)
				.contains("Clinic: GASTRO")
				.contains("Patient age: 58")
				.contains("Synthetic notes: six weeks of bloating.")
				.doesNotContain("{{clinic}}", "{{age}}", "{{clinicalNotes}}");
	}

	@Test
	void render_whenCalled_keepsJsonExampleBracesIntact() {
		String prompt = renderer.render(new TriageInput("CARDIOLOGY", 61, "Synthetic notes."));

		assertThat(prompt).contains(
				"{\"summary\": string, \"suggestedPriority\": \"URGENT\" | \"SOON\" | \"ROUTINE\", "
						+ "\"redFlags\": string[], \"confidence\": number between 0 and 1}");
	}

	@Test
	void render_whenNotesContainPlaceholderText_doesNotExpandIt() {
		String prompt = renderer.render(new TriageInput("DERMATOLOGY", 24, "Notes mention {{age}} and $1 literally."));

		assertThat(prompt).contains("Notes mention {{age}} and $1 literally.");
	}

	@Test
	void version_whenLoaded_isExposed() {
		assertThat(renderer.version()).isEqualTo("triage-v1");
	}

	@Test
	void constructor_whenPromptFileMissing_failsFast() {
		assertThatThrownBy(() -> new TriagePromptRenderer(new DefaultResourceLoader(), "triage-v999"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prompts/triage-v999.md");
	}

	@Test
	void constructor_whenVersionBlank_failsFast() {
		assertThatThrownBy(() -> new TriagePromptRenderer(new DefaultResourceLoader(), " "))
				.isInstanceOf(IllegalStateException.class);
	}
}
