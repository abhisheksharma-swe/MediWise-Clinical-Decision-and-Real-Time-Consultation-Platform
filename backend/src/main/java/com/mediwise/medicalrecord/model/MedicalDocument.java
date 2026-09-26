package com.mediwise.medicalrecord.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The patient's actual medical-document vault (lab reports, imaging, discharge
 * summaries). Distinct from ephemeral chat attachments — those stay as-is.
 */
@Entity
@Table(name = "medical_documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MedicalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "s3_key", nullable = false, columnDefinition = "TEXT")
    private String s3Key;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "related_appointment_id")
    private UUID relatedAppointmentId;

    @Column(name = "related_consultation_id")
    private UUID relatedConsultationId;

    @Column(name = "uploaded_at", updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }

    public enum DocumentType {
        LAB_REPORT, XRAY, MRI_CT, PRESCRIPTION_SCAN, DISCHARGE_SUMMARY, OTHER
    }
}
