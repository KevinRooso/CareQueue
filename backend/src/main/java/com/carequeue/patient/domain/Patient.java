package com.carequeue.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "patient")
public class Patient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String mrn;

	@Column(name = "first_name", nullable = false, length = 80)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 80)
	private String lastName;

	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	protected Patient() {
	}

	public Patient(String mrn, String firstName, String lastName, LocalDate dateOfBirth) {
		this.mrn = Objects.requireNonNull(mrn, "mrn is required");
		this.firstName = Objects.requireNonNull(firstName, "firstName is required");
		this.lastName = Objects.requireNonNull(lastName, "lastName is required");
		this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth is required");
	}

	public Long getId() {
		return id;
	}

	public String getMrn() {
		return mrn;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}
}
