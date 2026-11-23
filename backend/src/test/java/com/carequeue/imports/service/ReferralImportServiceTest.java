package com.carequeue.imports.service;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.imports.api.ImportResultDto;
import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.repository.DeadLetterMessageRepository;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralSource;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.repository.ReferralRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralImportServiceTest {

	private static final String VALID_JSON = """
			{"externalRef":"GP-2026-000481","referringGp":"Dr Synthetic","clinicCode":"GASTRO",
			 "patient":{"mrn":"MRN-000481","firstName":"Sam","lastName":"Test","dateOfBirth":"1968-04-12"},
			 "clinicalNotes":"synthetic notes"}""";

	@Mock
	private ReferralRepository referralRepository;

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private ClinicRepository clinicRepository;

	@Mock
	private DeadLetterMessageRepository deadLetterRepository;

	@Mock
	private PlatformTransactionManager transactionManager;

	private ReferralImportService service;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		lenient().when(deadLetterRepository.save(any())).thenAnswer(inv -> {
			DeadLetterMessage m = inv.getArgument(0);
			ReflectionTestUtils.setField(m, "id", 77L);
			return m;
		});
		service = new ReferralImportService(
				new ReferralMessageParser(JsonMapper.shared()),
				Validation.buildDefaultValidatorFactory().getValidator(),
				referralRepository, patientRepository, clinicRepository, deadLetterRepository,
				JsonMapper.shared(), new SimpleMeterRegistry(),
				Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC),
				transactionManager);
	}

	private static Referral existingReferral() {
		Patient p = new Patient("MRN-000481", "Sam", "Test", LocalDate.of(1968, 4, 12));
		Clinic c = new Clinic("GASTRO", "Gastro", "GASTRO");
		ReflectionTestUtils.setField(p, "id", 1L);
		ReflectionTestUtils.setField(c, "id", 1L);
		Referral r = Referral.receive(p, c, "Dr X", "GP-2026-000481", ReferralSource.JSON,
				"notes", null, Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(r, "id", 55L);
		return r;
	}

	@Test
	void importReferral_whenNew_createsReceivedReferral() {
		when(clinicRepository.findByCode("GASTRO"))
				.thenReturn(Optional.of(new Clinic("GASTRO", "Gastro", "GASTRO")));
		when(referralRepository.findByExternalRef("GP-2026-000481")).thenReturn(Optional.empty());
		when(patientRepository.findByMrn("MRN-000481")).thenReturn(Optional.empty());
		when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(referralRepository.save(any())).thenAnswer(inv -> {
			Referral r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 60L);
			return r;
		});

		ImportResultDto result = service.importReferral(VALID_JSON, ImportFormat.JSON);

		assertThat(result.created()).isTrue();
		assertThat(result.id()).isEqualTo(60L);
		verify(deadLetterRepository, never()).save(any());
	}

	@Test
	void importReferral_whenExternalRefExists_returnsDuplicateWithoutSaving() {
		when(clinicRepository.findByCode("GASTRO"))
				.thenReturn(Optional.of(new Clinic("GASTRO", "Gastro", "GASTRO")));
		when(referralRepository.findByExternalRef("GP-2026-000481"))
				.thenReturn(Optional.of(existingReferral()));

		ImportResultDto result = service.importReferral(VALID_JSON, ImportFormat.JSON);

		assertThat(result.created()).isFalse();
		assertThat(result.id()).isEqualTo(55L);
		verify(referralRepository, never()).save(any());
		verify(deadLetterRepository, never()).save(any());
	}

	@Test
	void importReferral_whenClinicUnknown_deadLettersAndThrows() {
		when(clinicRepository.findByCode("GASTRO")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.importReferral(VALID_JSON, ImportFormat.JSON))
				.isInstanceOfSatisfying(InvalidMessageException.class, ex -> {
					assertThat(ex.getDeadLetterId()).isEqualTo(77L);
					assertThat(ex.getMessage()).isEqualTo("Unknown clinic code.");
				});
		verify(deadLetterRepository).save(any(DeadLetterMessage.class));
		verify(referralRepository, never()).save(any());
	}

	@Test
	void importReferral_whenBeanValidationFails_deadLetterCarriesViolations() {
		String missingRef = VALID_JSON.replace("\"externalRef\":\"GP-2026-000481\",", "");

		assertThatThrownBy(() -> service.importReferral(missingRef, ImportFormat.JSON))
				.isInstanceOfSatisfying(InvalidMessageException.class, ex -> {
					assertThat(ex.getDeadLetterId()).isEqualTo(77L);
					assertThat(ex.getViolations()).anyMatch(v -> v.field().equals("externalRef"));
				});
		verify(deadLetterRepository).save(any(DeadLetterMessage.class));
		verify(clinicRepository, never()).findByCode(anyString());
	}

	@Test
	void importReferral_whenJsonMalformed_deadLettersWithoutViolations() {
		assertThatThrownBy(() -> service.importReferral("{\"oops\":", ImportFormat.JSON))
				.isInstanceOfSatisfying(InvalidMessageException.class, ex -> {
					assertThat(ex.getDeadLetterId()).isEqualTo(77L);
					assertThat(ex.getViolations()).isEmpty();
				});
		verify(deadLetterRepository).save(any(DeadLetterMessage.class));
	}

	@Test
	void importReferral_whenOversized_deadLetterStoresTruncatedPayload() {
		String huge = VALID_JSON + "x".repeat(ReferralImportService.MAX_PAYLOAD_CHARS);

		assertThatThrownBy(() -> service.importReferral(huge, ImportFormat.JSON))
				.isInstanceOf(InvalidMessageException.class);

		var captor = org.mockito.ArgumentCaptor.forClass(DeadLetterMessage.class);
		verify(deadLetterRepository).save(captor.capture());
		assertThat(captor.getValue().getPayload().length())
				.isEqualTo(ReferralImportService.MAX_PAYLOAD_CHARS);
	}
}
