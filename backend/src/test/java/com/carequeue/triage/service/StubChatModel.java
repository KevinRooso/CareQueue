package com.carequeue.triage.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A hand-written stub of Spring AI's {@link ChatModel}: no network, scripted answers.
 * Each step is either a reply (String) or an exception to throw; the last step repeats forever.
 */
final class StubChatModel implements ChatModel {

	private final List<Object> steps;
	private final AtomicInteger calls = new AtomicInteger();
	private volatile Prompt lastPrompt;

	private StubChatModel(List<Object> steps) {
		if (steps.isEmpty()) {
			throw new IllegalArgumentException("at least one step is required");
		}
		this.steps = List.copyOf(steps);
	}

	static StubChatModel of(Object... steps) {
		return new StubChatModel(List.of(steps));
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		int index = calls.getAndIncrement();
		lastPrompt = prompt;
		Object step = steps.get(Math.min(index, steps.size() - 1));
		if (step instanceof RuntimeException ex) {
			throw ex;
		}
		return new ChatResponse(List.of(new Generation(new AssistantMessage((String) step))));
	}

	int calls() {
		return calls.get();
	}

	Prompt lastPrompt() {
		return lastPrompt;
	}
}
