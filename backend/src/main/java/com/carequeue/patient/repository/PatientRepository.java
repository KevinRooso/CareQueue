package com.carequeue.patient.repository;

import com.carequeue.patient.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	Optional<Patient> findByMrn(String mrn);

	boolean existsByMrn(String mrn);
}
