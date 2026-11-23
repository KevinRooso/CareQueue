package com.carequeue.referral.api;

import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.domain.SuggestionSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReferralDetailDto(Long id, PatientSummaryDto patient, ClinicSummaryDto clinic,
		String referringGp, String externalRef, ReferralSource source, String clinicalNotes,
		String rawMessage, ReferralStatus status,
		String aiSummary, Priority suggestedPriority, SuggestionSource suggestionSource,
		BigDecimal suggestionConfidence, String promptVersion, List<String> redFlags,
		Priority confirmedPriority, Short priorityRank, String triageNote, String triagedBy,
		Instant triagedAt, Instant receivedAt, long version) {

	public static ReferralDetailDto from(Referral r) {
		return new ReferralDetailDto(r.getId(),
				PatientSummaryDto.from(r.getPatient()), ClinicSummaryDto.from(r.getClinic()),
				r.getReferringGp(), r.getExternalRef(), r.getSource(), r.getClinicalNotes(),
				r.getRawMessage(), r.getStatus(),
				r.getAiSummary(), r.getSuggestedPriority(), r.getSuggestionSource(),
				r.getSuggestionConfidence(), r.getPromptVersion(), r.getRedFlags(),
				r.getConfirmedPriority(), r.getPriorityRank(), r.getTriageNote(), r.getTriagedBy(),
				r.getTriagedAt(), r.getReceivedAt(), r.getVersion());
	}
}
