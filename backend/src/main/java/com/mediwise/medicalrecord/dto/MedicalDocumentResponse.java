package com.mediwise.medicalrecord.dto;

import com.mediwise.medicalrecord.model.MedicalDocument;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class MedicalDocumentResponse {
    private UUID id;
    private UUID patientId;
    private MedicalDocument.DocumentType documentType;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private UUID relatedAppointmentId;
    private UUID relatedConsultationId;
    /** Time-limited signed URL — generated fresh on every read, never persisted. */
    private String url;
    private Instant uploadedAt;

    public static MedicalDocumentResponse from(MedicalDocument d, String presignedUrl) {
        return MedicalDocumentResponse.builder()
                .id(d.getId())
                .patientId(d.getPatientId())
                .documentType(d.getDocumentType())
                .originalFilename(d.getOriginalFilename())
                .contentType(d.getContentType())
                .sizeBytes(d.getSizeBytes())
                .relatedAppointmentId(d.getRelatedAppointmentId())
                .relatedConsultationId(d.getRelatedConsultationId())
                .url(presignedUrl)
                .uploadedAt(d.getUploadedAt())
                .build();
    }
}
