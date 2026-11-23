package com.carequeue.referral.api;

import com.carequeue.patient.domain.Patient;

public record PatientSummaryDto(String mrn, String firstName, String lastName) {

	public static PatientSummaryDto from(Patient p) {
		return new PatientSummaryDto(p.getMrn(), p.getFirstName(), p.getLastName());
	}
}
