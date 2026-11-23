package com.carequeue.referral.api;

import jakarta.validation.constraints.NotNull;

public record RejectReferralRequest(@NotNull Long version) {
}
