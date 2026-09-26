package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.PatientAllergy;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class AllergyResponse {
    private UUID id;
    private String allergen;
    private String reaction;
    private PatientAllergy.Severity severity;
    private String notes;
    private boolean selfReported;

    public static AllergyResponse from(PatientAllergy a) {
        return AllergyResponse.builder()
                .id(a.getId()).allergen(a.getAllergen()).reaction(a.getReaction())
                .severity(a.getSeverity()).notes(a.getNotes()).selfReported(a.isSelfReported())
                .build();
    }
}
