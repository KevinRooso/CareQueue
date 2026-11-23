package com.carequeue.appointment.api;

import com.carequeue.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "appointments", description = "Booking appointments for triaged referrals")
public class AppointmentController {

	private final AppointmentService appointmentService;

	public AppointmentController(AppointmentService appointmentService) {
		this.appointmentService = appointmentService;
	}

	@PostMapping("/api/referrals/{referralId}/appointment")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Book an appointment for a TRIAGED referral")
	public AppointmentDto book(@PathVariable Long referralId,
			@Valid @RequestBody BookAppointmentRequest body) {
		return AppointmentDto.from(appointmentService.book(referralId, body.startsAt()));
	}
}
