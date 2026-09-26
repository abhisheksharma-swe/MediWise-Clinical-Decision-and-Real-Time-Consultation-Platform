package com.mediwise.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMedicationRequest {
    @NotBlank(message = "Medication name is required")
    private String name;
    private String dosage;
    private String frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Doctor-only: the prescription item this medication was carried over from. */
    private UUID sourcePrescriptionItemId;
}
