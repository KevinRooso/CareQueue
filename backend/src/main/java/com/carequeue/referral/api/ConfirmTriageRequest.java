package com.carequeue.referral.api;

import com.carequeue.referral.domain.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmTriageRequest(@NotNull Priority priority, @Size(max = 500) String note,
		@NotNull Long version) {
}
