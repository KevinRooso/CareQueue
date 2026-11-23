package com.carequeue.referral.repository;

import com.carequeue.referral.domain.Referral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long>, JpaSpecificationExecutor<Referral> {

	Optional<Referral> findByExternalRef(String externalRef);

	@Override
	@EntityGraph(attributePaths = {"patient", "clinic"})
	Page<Referral> findAll(Specification<Referral> spec, Pageable pageable);

	/**
	 * Referral with patient and clinic already fetched, so DTO mapping never
	 * triggers a lazy load (open-in-view is off — the persistence context is
	 * gone by the time the controller renders).
	 */
	@EntityGraph(attributePaths = {"patient", "clinic"})
	Optional<Referral> findWithDetailsById(Long id);

	/**
	 * The waiting list for one clinic: TRIAGED referrals ordered by confirmed
	 * priority then oldest first. Ordering is fixed by the query — callers pass an
	 * unsorted {@link PageRequest} for paging only.
	 */
	@Query(value = """
			select r from Referral r
			join fetch r.patient
			join fetch r.clinic
			where r.clinic.id = :clinicId
			and r.status = com.carequeue.referral.domain.ReferralStatus.TRIAGED
			order by r.priorityRank asc, r.receivedAt asc, r.id asc
			""",
			countQuery = """
			select count(r) from Referral r
			where r.clinic.id = :clinicId
			and r.status = com.carequeue.referral.domain.ReferralStatus.TRIAGED
			""")
	Page<Referral> findWaitingList(@Param("clinicId") Long clinicId, Pageable pageable);
}
