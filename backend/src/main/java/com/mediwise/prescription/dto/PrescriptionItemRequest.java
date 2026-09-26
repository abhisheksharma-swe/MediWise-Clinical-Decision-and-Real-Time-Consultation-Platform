package com.mediwise.prescription.dto;

import com.mediwise.prescription.model.PrescriptionItem;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PrescriptionItemRequest {

    @NotBlank(message = "Medicine name is required")
    private String medicineName;

    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
    private PrescriptionItem.BeforeAfterFood beforeAfterFood;
}
