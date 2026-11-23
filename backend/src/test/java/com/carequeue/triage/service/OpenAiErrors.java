package com.carequeue.triage.service;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;

import java.net.SocketTimeoutException;

/** The exceptions the OpenAI Java SDK (behind Spring AI 2.1) throws, built without any HTTP call. */
final class OpenAiErrors {

	private OpenAiErrors() {
	}

	/** What a read/call timeout or a dropped connection looks like. */
	static OpenAIIoException timeout() {
		return new OpenAIIoException("Request failed", new SocketTimeoutException("timeout"));
	}

	/** HTTP 429. */
	static RateLimitException rateLimited() {
		return RateLimitException.builder().headers(Headers.builder().build()).build();
	}

	/** HTTP 5xx. */
	static InternalServerException serverError(int status) {
		return InternalServerException.builder().statusCode(status).headers(Headers.builder().build()).build();
	}

	/** HTTP 401: not transient. */
	static UnauthorizedException unauthorized() {
		return UnauthorizedException.builder().headers(Headers.builder().build()).build();
	}
}
