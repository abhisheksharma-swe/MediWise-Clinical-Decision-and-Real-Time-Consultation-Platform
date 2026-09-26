package com.mediwise.followup.dto;

import com.mediwise.followup.model.FollowUp;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
public class FollowUpResponse {
    private UUID id;
    private UUID consultationId;
    private UUID patientId;
    private UUID doctorId;
    private LocalDate recommendedDate;
    private String reason;
    private FollowUp.Status status;
    private UUID linkedAppointmentId;
    private Instant createdAt;

    public static FollowUpResponse from(FollowUp f) {
        return FollowUpResponse.builder()
                .id(f.getId())
                .consultationId(f.getConsultationId())
                .patientId(f.getPatientId())
                .doctorId(f.getDoctorId())
                .recommendedDate(f.getRecommendedDate())
                .reason(f.getReason())
                .status(f.getStatus())
                .linkedAppointmentId(f.getLinkedAppointmentId())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
