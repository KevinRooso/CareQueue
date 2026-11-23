package com.carequeue.referral.domain;

public class InvalidStatusTransitionException extends RuntimeException {

	private final ReferralStatus from;
	private final ReferralStatus to;

	public InvalidStatusTransitionException(ReferralStatus from, ReferralStatus to) {
		super("Cannot move referral from " + from + " to " + to);
		this.from = from;
		this.to = to;
	}

	public ReferralStatus getFrom() {
		return from;
	}

	public ReferralStatus getTo() {
		return to;
	}
}
