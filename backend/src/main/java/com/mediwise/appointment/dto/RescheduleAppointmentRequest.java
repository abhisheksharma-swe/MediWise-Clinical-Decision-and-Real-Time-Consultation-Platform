package com.mediwise.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RescheduleAppointmentRequest {
    @NotNull(message = "New slot ID is required")
    private UUID newSlotId;
}
