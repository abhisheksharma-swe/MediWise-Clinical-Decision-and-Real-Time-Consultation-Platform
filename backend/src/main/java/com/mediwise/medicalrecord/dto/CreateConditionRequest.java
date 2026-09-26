package com.mediwise.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateConditionRequest {
    @NotBlank(message = "Condition name is required")
    private String name;
    private LocalDate diagnosedDate;
    private String notes;
    /** Doctor-only: the consultation this was diagnosed during. */
    private UUID sourceConsultationId;
}
