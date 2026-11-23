package com.carequeue.common.error;

import java.util.List;

/**
 * An import message that failed parsing, schema validation or bean validation.
 * Mapped to 422 problem+json by {@code GlobalExceptionHandler}. Carries an optional
 * list of field-level violations and the id of the dead-letter row that stored the
 * payload (set once the dead letter has been committed — see
 * {@link #withDeadLetterId(long)}).
 */
public class InvalidMessageException extends RuntimeException {

	public record Violation(String field, String message) {
	}

	private final List<Violation> violations;
	private Long deadLetterId;

	public InvalidMessageException(String detail) {
		super(detail);
		this.violations = List.of();
	}

	public InvalidMessageException(String detail, List<Violation> violations) {
		super(detail);
		this.violations = violations == null ? List.of() : List.copyOf(violations);
	}

	public List<Violation> getViolations() {
		return violations;
	}

	public Long getDeadLetterId() {
		return deadLetterId;
	}

	public InvalidMessageException withDeadLetterId(long id) {
		this.deadLetterId = id;
		return this;
	}
}
