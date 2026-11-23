package com.carequeue.audit.api;

import java.time.Instant;
import java.util.List;

/**
 * One row of a referral's change history (US-9): which Envers revision, when,
 * who, and the fields that changed between this revision and the previous one.
 * The CREATED revision lists the non-null initial values with {@code from = null}.
 */
public record ReferralRevisionDto(long revision, Instant timestamp, String username, String type,
		List<FieldChange> changes) {

	public record FieldChange(String field, String from, String to) {
	}
}
