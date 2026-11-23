package com.carequeue.referral.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ReferralStatus {
	RECEIVED,
	AI_SUGGESTED,
	TRIAGED,
	BOOKED,
	REJECTED;

	private static final Map<ReferralStatus, Set<ReferralStatus>> ALLOWED = Map.of(
			RECEIVED, EnumSet.of(AI_SUGGESTED, REJECTED),
			AI_SUGGESTED, EnumSet.of(AI_SUGGESTED, TRIAGED, REJECTED),
			TRIAGED, EnumSet.of(BOOKED, REJECTED),
			BOOKED, EnumSet.noneOf(ReferralStatus.class),
			REJECTED, EnumSet.noneOf(ReferralStatus.class));

	public boolean canMoveTo(ReferralStatus next) {
		return ALLOWED.get(this).contains(next);
	}
}
