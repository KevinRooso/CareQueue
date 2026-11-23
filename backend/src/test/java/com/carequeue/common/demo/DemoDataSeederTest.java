package com.carequeue.common.demo;

import com.carequeue.TestcontainersConfiguration;
import com.carequeue.appointment.repository.AppointmentRepository;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.domain.ReferralStatus;
import com.carequeue.referral.repository.ReferralRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Transactional
class DemoDataSeederTest {

	@Autowired
	private ReferralRepository referralRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private DemoDataSeeder seeder;

	@Autowired
	private EntityManager entityManager;

	@Test
	void run_onStart_seedsTwentyFiveAcrossStatusesAndClinics() {
		assertSeeded();
	}

	@Test
	void run_whenCalledAgain_isIdempotent() throws Exception {
		seeder.run(new DefaultApplicationArguments());
		assertSeeded();
	}

	private void assertSeeded() {
		var referrals = referralRepository.findAll();
		assertThat(referrals).hasSize(25);

		Map<ReferralStatus, Long> byStatus = referrals.stream()
				.collect(Collectors.groupingBy(Referral::getStatus,
						() -> new EnumMap<>(ReferralStatus.class), Collectors.counting()));
		assertThat(byStatus.get(ReferralStatus.RECEIVED)).isEqualTo(6);
		assertThat(byStatus.get(ReferralStatus.AI_SUGGESTED)).isEqualTo(6);
		assertThat(byStatus.get(ReferralStatus.TRIAGED)).isEqualTo(8);
		assertThat(byStatus.get(ReferralStatus.BOOKED)).isEqualTo(3);
		assertThat(byStatus.get(ReferralStatus.REJECTED)).isEqualTo(2);

		assertThat(appointmentRepository.count()).isEqualTo(3);

		assertThat(referrals.stream()
				.map(r -> r.getClinic().getCode())
				.collect(Collectors.toSet()))
				.containsExactlyInAnyOrder("GASTRO", "CARDIOLOGY", "DERMATOLOGY");

		assertThat(referrals)
				.allSatisfy(r -> assertThat(r.getExternalRef()).startsWith("DEMO-2026-"));

		// Seeding runs with no authenticated user, so the revision records "system".
		Object username = entityManager.createNativeQuery(
				"select username from revinfo order by rev limit 1")
				.getSingleResult();
		assertThat(username).isEqualTo("system");
	}
}
