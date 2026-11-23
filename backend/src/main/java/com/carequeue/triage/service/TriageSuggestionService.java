package com.carequeue.triage.service;

import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.referral.domain.InvalidStatusTransitionException;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.repository.ReferralRepository;
import com.carequeue.triage.domain.TriageInput;
import com.carequeue.triage.domain.TriageSuggestion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/**
 * Runs the AI (or rule-based) triage suggestion for one referral. Deliberately NOT one
 * long transaction: a Groq call with retries can take seconds, and holding a database
 * connection across it would starve the pool. The flow is a short read transaction to
 * build the input and capture the version, the AI call with no transaction at all,
 * then a short write transaction that re-checks the version before applying. The
 * suggestion is recorded only as suggestion fields — {@code confirmedPriority} is
 * untouched, because only a clinician's confirm sets it (ADR-005).
 */
@Service
public class TriageSuggestionService {

	private final ReferralRepository referralRepository;
	private final TriageAiClient triageAiClient;
	private final TransactionTemplate readTx;
	private final TransactionTemplate writeTx;
	private final Clock clock;

	public TriageSuggestionService(ReferralRepository referralRepository,
			TriageAiClient triageAiClient,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.referralRepository = referralRepository;
		this.triageAiClient = triageAiClient;
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
		this.writeTx = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public Referral suggest(Long referralId) {
		PreparedReferral prepared = readTx.execute(status -> {
			Referral r = referralRepository.findWithDetailsById(referralId)
					.orElseThrow(() -> new NotFoundException("Referral " + referralId + " not found"));
			if (r.getStatus() != ReferralStatus.RECEIVED && r.getStatus() != ReferralStatus.AI_SUGGESTED) {
				throw new InvalidStatusTransitionException(r.getStatus(), ReferralStatus.AI_SUGGESTED);
			}
			int age = Period.between(r.getPatient().getDateOfBirth(), LocalDate.now(clock)).getYears();
			return new PreparedReferral(new TriageInput(r.getClinic().getCode(), age, r.getClinicalNotes()),
					r.getVersion());
		});

		TriageSuggestion suggestion = triageAiClient.suggest(prepared.input());

		return writeTx.execute(status -> {
			Referral r = referralRepository.findWithDetailsById(referralId)
					.orElseThrow(() -> new NotFoundException("Referral " + referralId + " not found"));
			if (r.getVersion() != prepared.version()) {
				throw new StaleVersionException(prepared.version(), r.getVersion());
			}
			r.recordSuggestion(suggestion.summary(), suggestion.suggestedPriority(),
					suggestion.redFlags(),
					BigDecimal.valueOf(suggestion.confidence()).setScale(2, RoundingMode.HALF_UP),
					suggestion.promptVersion(), suggestion.source());
			return referralRepository.saveAndFlush(r);
		});
	}

	private record PreparedReferral(TriageInput input, long version) {
	}
}
