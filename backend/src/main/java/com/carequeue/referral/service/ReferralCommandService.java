package com.carequeue.referral.service;

import com.carequeue.common.error.NotFoundException;
import com.carequeue.common.error.StaleVersionException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.repository.ReferralRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ReferralCommandService {

	private final ReferralRepository referralRepository;
	private final Clock clock;

	public ReferralCommandService(ReferralRepository referralRepository, Clock clock) {
		this.referralRepository = referralRepository;
		this.clock = clock;
	}

	@Transactional
	public Referral confirm(Long id, Priority priority, String note, long version, String clinician) {
		Referral referral = getForUpdate(id);
		requireCurrentVersion(referral, version);
		referral.confirmPriority(priority, note, clinician, Instant.now(clock));
		return referralRepository.saveAndFlush(referral);
	}

	@Transactional
	public Referral reject(Long id, long version) {
		Referral referral = getForUpdate(id);
		requireCurrentVersion(referral, version);
		referral.reject();
		return referralRepository.saveAndFlush(referral);
	}

	private Referral getForUpdate(Long id) {
		return referralRepository.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("Referral " + id + " not found"));
	}

	private void requireCurrentVersion(Referral referral, long version) {
		if (referral.getVersion() != version) {
			throw new StaleVersionException(version, referral.getVersion());
		}
	}
}
