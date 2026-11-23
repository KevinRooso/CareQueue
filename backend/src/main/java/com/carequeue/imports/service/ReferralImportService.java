package com.carequeue.imports.service;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.imports.api.ImportReferralRequest;
import com.carequeue.imports.api.ImportResultDto;
import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.repository.DeadLetterMessageRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.repository.ReferralRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Imports one referral message (JSON or XML). Parse and validation happen outside any
 * transaction; the insert runs in one short transaction; a rejected message is stored
 * as a dead letter in its own committed (REQUIRES_NEW) transaction so it survives the
 * caller's rollback. Logs never contain payload content, patient names or MRNs —
 * only the dead-letter id and an error category.
 */
@Service
public class ReferralImportService {

	private static final Logger log = LoggerFactory.getLogger(ReferralImportService.class);

	static final int MAX_PAYLOAD_CHARS = 100_000;

	private final ReferralMessageParser parser;
	private final Validator validator;
	private final ReferralRepository referralRepository;
	private final PatientRepository patientRepository;
	private final ClinicRepository clinicRepository;
	private final DeadLetterMessageRepository deadLetterRepository;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	private final Clock clock;
	private final TransactionTemplate importTx;
	private final TransactionTemplate deadLetterTx;

	public ReferralImportService(ReferralMessageParser parser, Validator validator,
			ReferralRepository referralRepository, PatientRepository patientRepository,
			ClinicRepository clinicRepository, DeadLetterMessageRepository deadLetterRepository,
			ObjectMapper objectMapper, MeterRegistry meterRegistry, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.parser = parser;
		this.validator = validator;
		this.referralRepository = referralRepository;
		this.patientRepository = patientRepository;
		this.clinicRepository = clinicRepository;
		this.deadLetterRepository = deadLetterRepository;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
		this.clock = clock;
		this.importTx = new TransactionTemplate(transactionManager);
		this.deadLetterTx = new TransactionTemplate(transactionManager);
		this.deadLetterTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public ImportResultDto importReferral(String payload, ImportFormat format) {
		try {
			if (payload.length() > MAX_PAYLOAD_CHARS) {
				throw new InvalidMessageException("The message exceeds the maximum allowed size.");
			}
			ImportReferralRequest request = format == ImportFormat.JSON
					? parser.parseJson(payload)
					: parser.parseXml(payload);

			List<InvalidMessageException.Violation> violations = validator.validate(request).stream()
					.map(v -> new InvalidMessageException.Violation(
							v.getPropertyPath().toString(), v.getMessage()))
					.toList();
			if (!violations.isEmpty()) {
				throw new InvalidMessageException("The message failed validation.", violations);
			}

			String rawMessage = format == ImportFormat.JSON ? payload : wrapXmlAsJson(payload);
			ImportResultDto result = importTx.execute(status -> {
				Clinic clinic = clinicRepository.findByCode(request.clinicCode())
						.orElseThrow(() -> new InvalidMessageException("Unknown clinic code."));
				var existing = referralRepository.findByExternalRef(request.externalRef());
				if (existing.isPresent()) {
					return new ImportResultDto(existing.get().getId(), false);
				}
				Patient patient = patientRepository.findByMrn(request.patient().mrn())
						.orElseGet(() -> patientRepository.save(new Patient(request.patient().mrn(),
								request.patient().firstName(), request.patient().lastName(),
								request.patient().dateOfBirth())));
				Referral referral = Referral.receive(patient, clinic, request.referringGp(),
						request.externalRef(),
						format == ImportFormat.JSON ? ReferralSource.JSON : ReferralSource.XML,
						request.clinicalNotes(), rawMessage, Instant.now(clock));
				return new ImportResultDto(referralRepository.save(referral).getId(), true);
			});

			meterRegistry.counter("carequeue.imports",
					"result", result.created() ? "created" : "duplicate").increment();
			return result;
		}
		catch (InvalidMessageException ex) {
			long deadLetterId = deadLetterTx.execute(status -> deadLetterRepository
					.save(new DeadLetterMessage(format, truncate(payload), ex.getMessage(),
							Instant.now(clock)))
					.getId());
			ex.withDeadLetterId(deadLetterId);
			meterRegistry.counter("carequeue.imports", "result", "dead_letter").increment();
			log.warn("Import rejected, dead-letter {} saved", deadLetterId);
			throw ex;
		}
	}

	private String wrapXmlAsJson(String xml) {
		return objectMapper.writeValueAsString(Map.of("xml", xml));
	}

	private static String truncate(String payload) {
		return payload.length() > MAX_PAYLOAD_CHARS ? payload.substring(0, MAX_PAYLOAD_CHARS) : payload;
	}
}
