package com.carequeue.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "clinic")
public class Clinic {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 30)
	private String code;

	@Column(nullable = false, unique = true, length = 120)
	private String name;

	@Column(nullable = false, length = 60)
	private String specialty;

	protected Clinic() {
	}

	public Clinic(String code, String name, String specialty) {
		this.code = Objects.requireNonNull(code, "code is required");
		this.name = Objects.requireNonNull(name, "name is required");
		this.specialty = Objects.requireNonNull(specialty, "specialty is required");
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getSpecialty() {
		return specialty;
	}
}
