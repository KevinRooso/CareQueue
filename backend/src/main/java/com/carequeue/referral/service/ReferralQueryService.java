package com.carequeue.referral.service;

import com.carequeue.common.error.BadSortException;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.referral.domain.Priority;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.repository.ReferralRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReferralQueryService {

	private static final Set<String> ALLOWED_SORT_FIELDS =
			Set.of("receivedAt", "triagedAt", "status", "priorityRank");

	private final ReferralRepository referralRepository;

	public ReferralQueryService(ReferralRepository referralRepository) {
		this.referralRepository = referralRepository;
	}

	public Page<Referral> search(ReferralStatus status, Priority priority, Long clinicId,
			Pageable pageable) {
		requireAllowedSort(pageable);
		Specification<Referral> spec = (root, query, cb) -> cb.conjunction();
		if (clinicId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("clinic").get("id"), clinicId));
		}
		if (status != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}
		if (priority != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("confirmedPriority"), priority));
		}
		return referralRepository.findAll(spec, pageable);
	}

	public Referral getById(Long id) {
		return referralRepository.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("Referral " + id + " not found"));
	}

	public Page<Referral> waitingList(Long clinicId, Pageable pageable) {
		return referralRepository.findWaitingList(clinicId, pageable);
	}

	private void requireAllowedSort(Pageable pageable) {
		for (var order : pageable.getSort()) {
			if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
				throw new BadSortException(order.getProperty());
			}
		}
	}
}
