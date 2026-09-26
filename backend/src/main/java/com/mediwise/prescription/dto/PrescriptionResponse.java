package com.mediwise.prescription.dto;

import com.mediwise.prescription.model.Prescription;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class PrescriptionResponse {
    private UUID id;
    private UUID consultationId;
    private UUID patientId;
    private UUID doctorId;
    private String notes;
    private List<PrescriptionItemResponse> items;
    private Instant createdAt;

    public static PrescriptionResponse from(Prescription p, List<PrescriptionItemResponse> items) {
        return PrescriptionResponse.builder()
                .id(p.getId())
                .consultationId(p.getConsultationId())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .notes(p.getNotes())
                .items(items)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
