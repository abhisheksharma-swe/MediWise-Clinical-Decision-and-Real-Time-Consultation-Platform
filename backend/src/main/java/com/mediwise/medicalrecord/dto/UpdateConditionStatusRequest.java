package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.PatientCondition;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConditionStatusRequest {
    @NotNull(message = "Status is required")
    private PatientCondition.Status status;
}
