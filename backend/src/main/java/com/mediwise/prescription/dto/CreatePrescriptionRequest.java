package com.mediwise.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreatePrescriptionRequest {

    private String notes;

    @NotEmpty(message = "At least one prescription item is required")
    @Valid
    private List<PrescriptionItemRequest> items;
}
