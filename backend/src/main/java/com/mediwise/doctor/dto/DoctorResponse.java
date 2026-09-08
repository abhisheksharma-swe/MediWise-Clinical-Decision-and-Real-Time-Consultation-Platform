package com.mediwise.doctor.dto;

import com.mediwise.doctor.model.Doctor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorResponse {
    private UUID id;
    private UUID userId;
    private String fullName;
    private String bio;
    private String specialty;
    private Set<String> specialties;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private String profileImage;
    private BigDecimal avgRating;
    private Integer totalReviews;
    private boolean available;
    private boolean verified;

    public static DoctorResponse from(Doctor d) {
        return DoctorResponse.builder()
                .id(d.getId())
                .userId(d.getUserId())
                .fullName(d.getFullName())
                .bio(d.getBio())
                .specialty(d.getSpecialty())
                // Copy into a plain HashSet — d.getSpecialties() is Hibernate's own
                // PersistentSet wrapper, which can't be serialized/deserialized safely
                // once detached from its (already-closed) Session.
                .specialties(d.getSpecialties() == null ? null : new HashSet<>(d.getSpecialties()))
                .experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee())
                .profileImage(d.getProfileImage())
                .avgRating(d.getAvgRating())
                .totalReviews(d.getTotalReviews())
                .available(d.isAvailable())
                .verified(d.isVerified())
                .build();
    }
}
