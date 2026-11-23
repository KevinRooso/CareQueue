package com.carequeue.imports.api;

/** Result of an import: the referral id and whether it was newly created (vs a duplicate). */
public record ImportResultDto(long id, boolean created) {
}
