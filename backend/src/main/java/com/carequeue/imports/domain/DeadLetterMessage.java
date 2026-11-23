package com.carequeue.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "dead_letter_message")
public class DeadLetterMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ImportFormat source;

	@Column(nullable = false)
	private String payload;

	@Column(nullable = false)
	private String error;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	protected DeadLetterMessage() {
	}

	public DeadLetterMessage(ImportFormat source, String payload, String error, Instant receivedAt) {
		this.source = Objects.requireNonNull(source, "source is required");
		this.payload = Objects.requireNonNull(payload, "payload is required");
		this.error = Objects.requireNonNull(error, "error is required");
		this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt is required");
	}

	public Long getId() {
		return id;
	}

	public ImportFormat getSource() {
		return source;
	}

	public String getPayload() {
		return payload;
	}

	public String getError() {
		return error;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}
}
