package com.mediwise.review.dto;

import com.mediwise.review.model.DoctorRating;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class ReviewResponse {
    private UUID id;
    private UUID appointmentId;
    private UUID doctorId;
    private String patientName;
    private int rating;
    private String reviewText;
    private Instant createdAt;

    public static ReviewResponse from(DoctorRating r, String patientName) {
        return ReviewResponse.builder()
                .id(r.getId())
                .appointmentId(r.getAppointmentId())
                .doctorId(r.getDoctorId())
                .patientName(patientName)
                .rating(r.getRating())
                .reviewText(r.getReview())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
