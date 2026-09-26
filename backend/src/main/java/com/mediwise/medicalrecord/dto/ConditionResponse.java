package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.PatientCondition;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
public class ConditionResponse {
    private UUID id;
    private String name;
    private LocalDate diagnosedDate;
    private PatientCondition.Status status;
    private String notes;
    private boolean selfReported;

    public static ConditionResponse from(PatientCondition c) {
        return ConditionResponse.builder()
                .id(c.getId()).name(c.getName()).diagnosedDate(c.getDiagnosedDate())
                .status(c.getStatus()).notes(c.getNotes()).selfReported(c.isSelfReported())
                .build();
    }
}
