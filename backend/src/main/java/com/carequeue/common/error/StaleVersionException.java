package com.carequeue.common.error;

/**
 * The client's copy of an entity is older than the database row — the request
 * must be retried with the current version. Mapped to 409 by GlobalExceptionHandler.
 */
public class StaleVersionException extends RuntimeException {

	private final long expected;
	private final long actual;

	public StaleVersionException(long expected, long actual) {
		super("stale version: expected " + expected + " but current is " + actual);
		this.expected = expected;
		this.actual = actual;
	}

	public long getExpected() {
		return expected;
	}

	public long getActual() {
		return actual;
	}
}
