package com.carequeue.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.time.Instant;

@Entity
@Table(name = "revinfo")
@RevisionEntity(UsernameRevisionListener.class)
public class AuditRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@RevisionNumber
	@Column(name = "rev")
	private Long id;

	@RevisionTimestamp
	@Column(name = "revtstmp")
	private Instant timestamp;

	@Column(name = "username", nullable = false, length = 60)
	private String username;

	public Long getId() {
		return id;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public String getUsername() {
		return username;
	}

	void setUsername(String username) {
		this.username = username;
	}
}
