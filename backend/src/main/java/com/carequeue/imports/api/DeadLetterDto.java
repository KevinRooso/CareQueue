package com.carequeue.imports.api;

import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.domain.ImportFormat;

import java.time.Instant;

public record DeadLetterDto(Long id, ImportFormat source, String payload, String error,
		Instant receivedAt) {

	public static DeadLetterDto from(DeadLetterMessage m) {
		return new DeadLetterDto(m.getId(), m.getSource(), m.getPayload(), m.getError(),
				m.getReceivedAt());
	}
}
