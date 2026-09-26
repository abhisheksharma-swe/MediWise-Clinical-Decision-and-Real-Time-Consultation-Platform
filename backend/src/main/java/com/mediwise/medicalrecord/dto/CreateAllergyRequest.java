package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.PatientAllergy;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAllergyRequest {
    @NotBlank(message = "Allergen is required")
    private String allergen;
    private String reaction;
    private PatientAllergy.Severity severity;
    private String notes;
}
