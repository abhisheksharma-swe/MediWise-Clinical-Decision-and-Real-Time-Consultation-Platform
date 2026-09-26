package com.mediwise.review.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Maps the `doctor_ratings` table, created in V1 but never mapped/wired until now. */
@Entity
@Table(name = "doctor_ratings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", unique = true, nullable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(nullable = false)
    private short rating;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
