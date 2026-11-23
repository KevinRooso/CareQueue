package com.carequeue.referral.api;

import com.carequeue.clinic.domain.Clinic;

public record ClinicSummaryDto(Long id, String code, String name, String specialty) {

	public static ClinicSummaryDto from(Clinic c) {
		return new ClinicSummaryDto(c.getId(), c.getCode(), c.getName(), c.getSpecialty());
	}
}
