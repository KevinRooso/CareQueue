package com.carequeue.appointment.domain;

import com.carequeue.referral.domain.Referral;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "appointment")
@Audited
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// One-way on purpose: an inverse one-to-one cannot be lazy, which would be an N+1 trap.
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "referral_id")
	private Referral referral;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentStatus status;

	protected Appointment() {
	}

	private Appointment(Referral referral, Instant startsAt) {
		this.referral = Objects.requireNonNull(referral, "referral is required");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt is required");
		this.status = AppointmentStatus.BOOKED;
	}

	public static Appointment bookFor(Referral referral, Instant startsAt) {
		referral.markBooked();
		return new Appointment(referral, startsAt);
	}

	public void cancel() {
		this.status = AppointmentStatus.CANCELLED;
	}

	public Long getId() {
		return id;
	}

	public Referral getReferral() {
		return referral;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public AppointmentStatus getStatus() {
		return status;
	}
}
