package com.carequeue.imports.repository;

import com.carequeue.imports.domain.DeadLetterMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, Long> {

	Page<DeadLetterMessage> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
