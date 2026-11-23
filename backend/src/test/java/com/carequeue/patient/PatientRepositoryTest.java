package com.carequeue.patient;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.patient.domain.Patient;
import com.carequeue.patient.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class PatientRepositoryTest {

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void save_whenReloaded_restoresAllFields() {
		Patient saved = patientRepository.save(
				new Patient("MRN-100001", "Alex", "Sample", LocalDate.of(1980, 5, 12)));
		entityManager.flush();
		entityManager.clear();

		Patient found = patientRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getMrn()).isEqualTo("MRN-100001");
		assertThat(found.getFirstName()).isEqualTo("Alex");
		assertThat(found.getLastName()).isEqualTo("Sample");
		assertThat(found.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 12));
	}

	@Test
	void findByMrn_whenExists_returnsPatient() {
		patientRepository.save(new Patient("MRN-100002", "Sam", "Test", LocalDate.of(1990, 1, 1)));
		assertThat(patientRepository.findByMrn("MRN-100002")).isPresent();
		assertThat(patientRepository.findByMrn("MRN-999999")).isEmpty();
		assertThat(patientRepository.existsByMrn("MRN-100002")).isTrue();
		assertThat(patientRepository.existsByMrn("MRN-999999")).isFalse();
	}

	@Test
	void save_whenDuplicateMrn_fails() {
		patientRepository.save(new Patient("MRN-100003", "A", "One", LocalDate.of(1980, 1, 1)));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> {
			patientRepository.save(new Patient("MRN-100003", "B", "Two", LocalDate.of(1990, 1, 1)));
			entityManager.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}
}
