package com.carequeue.triage.service;

import com.carequeue.triage.domain.TriageInput;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the versioned prompt {@code classpath:prompts/{version}.md} once, at startup, and fills in
 * {@code {{clinic}}}, {@code {{age}}} and {@code {{clinicalNotes}}}.
 *
 * <p>Plain string replacement on purpose, not Spring AI's {@code PromptTemplate}: the prompt contains a
 * JSON example whose braces a template engine would try to interpret. Replacement is a single pass,
 * so placeholder-like text inside the GP's notes is never expanded.
 */
public class TriagePromptRenderer {

	private static final List<String> PLACEHOLDERS = List.of("clinic", "age", "clinicalNotes");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(clinic|age|clinicalNotes)}}");

	private final String version;
	private final String template;

	public TriagePromptRenderer(ResourceLoader resourceLoader, String version) {
		Objects.requireNonNull(resourceLoader, "resourceLoader is required");
		if (version == null || version.isBlank()) {
			throw new IllegalStateException("carequeue.triage.prompt-version must be set");
		}
		this.version = version;
		this.template = load(resourceLoader, version);
	}

	/** The prompt version, saved on every AI suggestion (e.g. "triage-v1"). */
	public String version() {
		return version;
	}

	public String render(TriageInput input) {
		Map<String, String> values = Map.of(
				"clinic", input.clinic(),
				"age", Integer.toString(input.age()),
				"clinicalNotes", input.clinicalNotes());
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder out = new StringBuilder(template.length() + input.clinicalNotes().length());
		while (matcher.find()) {
			matcher.appendReplacement(out, Matcher.quoteReplacement(values.get(matcher.group(1))));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String load(ResourceLoader resourceLoader, String version) {
		String location = "classpath:prompts/" + version + ".md";
		Resource resource = resourceLoader.getResource(location);
		if (!resource.exists()) {
			throw new IllegalStateException("Triage prompt not found: " + location);
		}
		String text;
		try {
			text = resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read triage prompt " + location, ex);
		}
		for (String name : PLACEHOLDERS) {
			if (!text.contains("{{" + name + "}}")) {
				throw new IllegalStateException("Triage prompt " + location + " has no {{" + name + "}} placeholder");
			}
		}
		return text;
	}
}
