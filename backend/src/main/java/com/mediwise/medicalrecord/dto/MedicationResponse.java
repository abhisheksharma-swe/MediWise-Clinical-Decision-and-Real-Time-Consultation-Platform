package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.PatientMedication;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
public class MedicationResponse {
    private UUID id;
    private String name;
    private String dosage;
    private String frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
    private boolean selfReported;

    public static MedicationResponse from(PatientMedication m) {
        return MedicationResponse.builder()
                .id(m.getId()).name(m.getName()).dosage(m.getDosage()).frequency(m.getFrequency())
                .startDate(m.getStartDate()).endDate(m.getEndDate())
                .active(m.isActive()).selfReported(m.isSelfReported())
                .build();
    }
}
