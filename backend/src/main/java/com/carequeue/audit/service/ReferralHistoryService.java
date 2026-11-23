package com.carequeue.audit.service;

import com.carequeue.audit.api.ReferralRevisionDto;
import com.carequeue.audit.api.ReferralRevisionDto.FieldChange;
import com.carequeue.audit.domain.AuditRevision;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * US-9: the change history of a referral, built from the Envers audit tables.
 * Each revision is compared with the previous snapshot over a fixed list of
 * triage fields, so the API answers "who changed what, when" without exposing
 * raw audit rows. Snapshots' lazy associations (patient, clinic) are never
 * touched — only the audited scalar fields below.
 */
@Service
public class ReferralHistoryService {

	private final EntityManager entityManager;
	private final ReferralRepository referralRepository;

	public ReferralHistoryService(EntityManager entityManager, ReferralRepository referralRepository) {
		this.entityManager = entityManager;
		this.referralRepository = referralRepository;
	}

	/** Audited fields that matter to a clinician reviewing triage history. */
	static final Map<String, Function<Referral, Object>> TRACKED_FIELDS;
	static {
		Map<String, Function<Referral, Object>> fields = new LinkedHashMap<>();
		fields.put("status", Referral::getStatus);
		fields.put("suggestedPriority", Referral::getSuggestedPriority);
		fields.put("suggestionSource", Referral::getSuggestionSource);
		fields.put("suggestionConfidence", Referral::getSuggestionConfidence);
		fields.put("promptVersion", Referral::getPromptVersion);
		fields.put("aiSummary", Referral::getAiSummary);
		fields.put("redFlags", Referral::getRedFlags);
		fields.put("confirmedPriority", Referral::getConfirmedPriority);
		fields.put("triageNote", Referral::getTriageNote);
		fields.put("triagedBy", Referral::getTriagedBy);
		fields.put("triagedAt", Referral::getTriagedAt);
		TRACKED_FIELDS = Map.copyOf(fields);
	}

	@Transactional(readOnly = true)
	public List<ReferralRevisionDto> history(Long referralId) {
		if (!referralRepository.existsById(referralId)) {
			throw new NotFoundException("Referral " + referralId + " not found");
		}
		@SuppressWarnings("unchecked")
		List<Object[]> rows = AuditReaderFactory.get(entityManager).createQuery()
				.forRevisionsOfEntity(Referral.class, false, true)
				.add(AuditEntity.id().eq(referralId))
				.addOrder(AuditEntity.revisionNumber().asc())
				.getResultList();

		List<ReferralRevisionDto> history = new ArrayList<>();
		Referral previous = null;
		for (Object[] row : rows) {
			Referral snapshot = (Referral) row[0];
			AuditRevision revision = (AuditRevision) row[1];
			RevisionType type = (RevisionType) row[2];
			history.add(new ReferralRevisionDto(revision.getId(), revision.getTimestamp(),
					revision.getUsername(), apiType(type), changesBetween(previous, snapshot)));
			previous = snapshot;
		}
		return history;
	}

	/**
	 * Field-level diff between two snapshots. For the first revision
	 * ({@code previous == null}) every non-null value shows up as
	 * {@code from = null} — i.e. the initial state at import.
	 */
	static List<FieldChange> changesBetween(Referral previous, Referral current) {
		List<FieldChange> changes = new ArrayList<>();
		for (Map.Entry<String, Function<Referral, Object>> field : TRACKED_FIELDS.entrySet()) {
			String from = previous == null ? null : render(field.getValue().apply(previous));
			String to = render(field.getValue().apply(current));
			if (!Objects.equals(from, to)) {
				changes.add(new FieldChange(field.getKey(), from, to));
			}
		}
		return changes;
	}

	/** Envers' internal names → the public vocabulary (CREATED/UPDATED/DELETED). */
	private static String apiType(RevisionType type) {
		return switch (type) {
			case ADD -> "CREATED";
			case MOD -> "UPDATED";
			case DEL -> "DELETED";
			default -> type.name();
		};
	}

	private static String render(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				return null;
			}
			StringBuilder joined = new StringBuilder();
			for (Object item : list) {
				if (!joined.isEmpty()) {
					joined.append(", ");
				}
				joined.append(item);
			}
			return joined.toString();
		}
		return String.valueOf(value);
	}
}
