package com.carequeue.clinic.service;

import com.carequeue.clinic.domain.Clinic;
import com.carequeue.clinic.repository.ClinicRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClinicService {

	private final ClinicRepository clinicRepository;

	public ClinicService(ClinicRepository clinicRepository) {
		this.clinicRepository = clinicRepository;
	}

	public List<Clinic> findAll() {
		return clinicRepository.findAll(Sort.by("name"));
	}
}
