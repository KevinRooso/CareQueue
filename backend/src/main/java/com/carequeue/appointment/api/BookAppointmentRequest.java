package com.carequeue.appointment.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookAppointmentRequest(@NotNull @Future Instant startsAt) {
}
