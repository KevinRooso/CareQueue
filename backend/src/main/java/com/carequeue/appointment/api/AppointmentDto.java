package com.carequeue.appointment.api;

import com.carequeue.appointment.domain.Appointment;
import com.carequeue.appointment.domain.AppointmentStatus;

import java.time.Instant;

public record AppointmentDto(Long id, Long referralId, Instant startsAt, AppointmentStatus status) {

	public static AppointmentDto from(Appointment a) {
		return new AppointmentDto(a.getId(), a.getReferral().getId(), a.getStartsAt(), a.getStatus());
	}
}
