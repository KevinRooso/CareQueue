package com.carequeue.imports;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.imports.domain.DeadLetterMessage;
import com.carequeue.imports.domain.ImportFormat;
import com.carequeue.imports.repository.DeadLetterMessageRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class DeadLetterMessageRepositoryTest {

	@Autowired
	private DeadLetterMessageRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void save_whenReloaded_restoresAllFields() {
		DeadLetterMessage saved = repository.save(new DeadLetterMessage(
				ImportFormat.XML,
				"<referral><bad-xml",
				"XML parse error at line 1",
				Instant.parse("2026-01-10T09:00:00Z")));
		entityManager.flush();
		entityManager.clear();

		DeadLetterMessage found = repository.findById(saved.getId()).orElseThrow();
		assertThat(found.getSource()).isEqualTo(ImportFormat.XML);
		assertThat(found.getPayload()).isEqualTo("<referral><bad-xml");
		assertThat(found.getError()).isEqualTo("XML parse error at line 1");
		assertThat(found.getReceivedAt()).isEqualTo(Instant.parse("2026-01-10T09:00:00Z"));
	}
}
