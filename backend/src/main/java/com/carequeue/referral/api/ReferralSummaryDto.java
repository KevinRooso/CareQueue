package com.carequeue.referral.api;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralStatus;

import java.time.Instant;

public record ReferralSummaryDto(Long id, ReferralStatus status, PatientSummaryDto patient,
		ClinicSummaryDto clinic, Priority suggestedPriority, Priority confirmedPriority,
		Short priorityRank, Instant receivedAt, Instant triagedAt, long version) {

	public static ReferralSummaryDto from(Referral r) {
		return new ReferralSummaryDto(r.getId(), r.getStatus(),
				PatientSummaryDto.from(r.getPatient()), ClinicSummaryDto.from(r.getClinic()),
				r.getSuggestedPriority(), r.getConfirmedPriority(), r.getPriorityRank(),
				r.getReceivedAt(), r.getTriagedAt(), r.getVersion());
	}
}
