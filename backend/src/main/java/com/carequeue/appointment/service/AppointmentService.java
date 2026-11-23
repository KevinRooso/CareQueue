package com.carequeue.appointment.service;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.repository.AppointmentRepository;
import com.carequeue.common.error.NotFoundException;
import com.carequeue.referral.domain.Referral;
import com.carequeue.referral.repository.ReferralRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AppointmentService {

	private final ReferralRepository referralRepository;
	private final AppointmentRepository appointmentRepository;

	public AppointmentService(ReferralRepository referralRepository,
			AppointmentRepository appointmentRepository) {
		this.referralRepository = referralRepository;
		this.appointmentRepository = appointmentRepository;
	}

	@Transactional
	public Appointment book(Long referralId, Instant startsAt) {
		Referral referral = referralRepository.findById(referralId)
				.orElseThrow(() -> new NotFoundException("Referral " + referralId + " not found"));
		return appointmentRepository.save(Appointment.bookFor(referral, startsAt));
	}
}
