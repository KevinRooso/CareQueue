package com.carequeue.referral.api;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;

import java.time.Instant;

public record WaitingListEntryDto(Long id, PatientSummaryDto patient, Priority confirmedPriority,
		Short priorityRank, Instant receivedAt, String triagedBy) {

	public static WaitingListEntryDto from(Referral r) {
		return new WaitingListEntryDto(r.getId(), PatientSummaryDto.from(r.getPatient()),
				r.getConfirmedPriority(), r.getPriorityRank(), r.getReceivedAt(), r.getTriagedBy());
	}
}
