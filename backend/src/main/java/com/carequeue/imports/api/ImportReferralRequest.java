package com.carequeue.imports.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * One imported referral message — the target shape for both JSON and XML imports.
 * Unknown JSON properties are ignored; XML goes through the XSD first.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportReferralRequest(
		@NotBlank @Size(max = 64) String externalRef,
		@NotBlank @Size(max = 120) String referringGp,
		@NotBlank @Size(max = 30) String clinicCode,
		@NotNull @Valid ImportPatient patient,
		@NotBlank @Size(max = 10000) String clinicalNotes) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ImportPatient(
			@NotBlank @Size(max = 20) String mrn,
			@NotBlank @Size(max = 80) String firstName,
			@NotBlank @Size(max = 80) String lastName,
			@NotNull @Past LocalDate dateOfBirth) {
	}
}
