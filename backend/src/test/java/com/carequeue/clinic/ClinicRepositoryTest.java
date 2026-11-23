package com.carequeue.clinic;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ClinicRepositoryTest {

	@Autowired
	private ClinicRepository clinicRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void findByCode_whenV3Seeded_returnsReferenceClinics() {
		// Proves the V3 reference-data migration ran in the test database.
		assertThat(clinicRepository.findByCode("GASTRO")).isPresent();
		assertThat(clinicRepository.findByCode("CARDIOLOGY")).isPresent();
		assertThat(clinicRepository.findByCode("DERMATOLOGY")).isPresent();
		assertThat(clinicRepository.findByCode("GASTRO").orElseThrow().getSpecialty())
				.isEqualTo("GASTRO");
	}

	@Test
	void save_whenReloaded_restoresAllFields() {
		Clinic saved = clinicRepository.save(new Clinic("TESTCLINIC", "Test Clinic", "TESTING"));
		entityManager.flush();
		entityManager.clear();

		Clinic found = clinicRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getCode()).isEqualTo("TESTCLINIC");
		assertThat(found.getName()).isEqualTo("Test Clinic");
		assertThat(found.getSpecialty()).isEqualTo("TESTING");
	}

	@Test
	void save_whenDuplicateCode_fails() {
		clinicRepository.save(new Clinic("DUP", "Clinic One", "TESTING"));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> {
			clinicRepository.save(new Clinic("DUP", "Clinic Two", "TESTING"));
			entityManager.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}
}
