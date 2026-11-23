package com.carequeue.clinic.repository;

import com.carequeue.clinic.domain.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

	Optional<Clinic> findByCode(String code);
}
