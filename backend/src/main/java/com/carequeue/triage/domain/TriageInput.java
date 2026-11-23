package com.carequeue.triage.domain;

import java.util.Objects;

/**
 * What the triage clients see of a referral: the clinic code, the patient's age and the GP's notes.
 * No names or identifiers, so nothing that identifies the patient is sent to the model.
 */
public record TriageInput(String clinic, int age, String clinicalNotes) {

	public TriageInput {
		Objects.requireNonNull(clinic, "clinic is required");
		Objects.requireNonNull(clinicalNotes, "clinicalNotes is required");
	}
}
