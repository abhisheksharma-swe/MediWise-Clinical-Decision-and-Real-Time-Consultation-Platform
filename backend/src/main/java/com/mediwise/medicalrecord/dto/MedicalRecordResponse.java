package com.mediwise.medicalrecord.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data @Builder
public class MedicalRecordResponse {
    private UUID patientId;
    private List<ConditionResponse> conditions;
    private List<AllergyResponse> allergies;
    private List<MedicationResponse> medications;
}
