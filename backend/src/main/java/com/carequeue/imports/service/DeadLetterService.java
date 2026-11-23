package com.carequeue.imports.service;

import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.repository.DeadLetterMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeadLetterService {

	private final DeadLetterMessageRepository repository;

	public DeadLetterService(DeadLetterMessageRepository repository) {
		this.repository = repository;
	}

	public Page<DeadLetterMessage> page(Pageable pageable) {
		return repository.findAllByOrderByReceivedAtDesc(pageable);
	}
}
