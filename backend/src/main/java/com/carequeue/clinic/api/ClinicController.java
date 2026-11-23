package com.carequeue.clinic.api;

import com.carequeue.clinic.service.ClinicService;
import com.carequeue.referral.api.ClinicSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('CLINICIAN')")
@Tag(name = "clinics", description = "Clinic reference data")
public class ClinicController {

	private final ClinicService clinicService;

	public ClinicController(ClinicService clinicService) {
		this.clinicService = clinicService;
	}

	@GetMapping("/api/clinics")
	@Operation(summary = "All clinics")
	public List<ClinicSummaryDto> list() {
		return clinicService.findAll().stream().map(ClinicSummaryDto::from).toList();
	}
}
