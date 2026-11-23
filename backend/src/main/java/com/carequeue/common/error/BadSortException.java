package com.carequeue.common.error;

/** Sort property outside the allowlist. Mapped to 400 by GlobalExceptionHandler. */
public class BadSortException extends RuntimeException {

	public BadSortException(String property) {
		super("Cannot sort by '" + property + "'");
	}
}
