package com.mediwise.ai.dto;

import com.mediwise.doctor.dto.DoctorResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AiReportResponse {

    private String id;
    private UUID patientId;
    private UUID appointmentId;

    private String modelName;
    private String modelVersion;

    private int urgencyScore;

    private String suggestedSpecialty;

    private double confidence;

    private String recommendation;

    private List<String> riskFactors;

    /** Verified doctors matching the suggested specialty — empty list if none found */
    private List<DoctorResponse> matchedDoctors;

    private Instant createdAt;
}