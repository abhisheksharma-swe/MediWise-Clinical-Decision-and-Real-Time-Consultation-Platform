package com.mediwise.medicalrecord.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_allergies")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PatientAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private String allergen;

    @Column(columnDefinition = "TEXT")
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Severity severity;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "self_reported", nullable = false)
    @Builder.Default
    private boolean selfReported = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Severity {
        MILD, MODERATE, SEVERE
    }
}
