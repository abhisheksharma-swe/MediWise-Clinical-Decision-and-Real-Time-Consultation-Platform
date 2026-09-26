package com.mediwise.medicalrecord.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.common.storage.S3StorageService;
import com.mediwise.medicalrecord.dto.MedicalDocumentResponse;
import com.mediwise.medicalrecord.model.MedicalDocument;
import com.mediwise.medicalrecord.repository.MedicalDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalDocumentService {

    private final MedicalDocumentRepository documentRepository;
    private final S3StorageService s3StorageService;
    private final AppointmentAuthorizationService authorizationService;

    @Value("${application.s3.presigned-url-ttl-minutes:15}")
    private long presignedUrlTtlMinutes;

    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    @Transactional
    public MedicalDocumentResponse upload(UUID patientId, User user, MultipartFile file,
                                           MedicalDocument.DocumentType documentType,
                                           UUID relatedAppointmentId, UUID relatedConsultationId) {
        if (user.getRole() == User.Role.PATIENT) {
            UUID ownPatientId = authorizationService.resolvePatientId(user);
            if (!patientId.equals(ownPatientId)) {
                throw new BusinessException("FORBIDDEN", "You can only upload documents to your own record.");
            }
        } else if (user.getRole() == User.Role.DOCTOR) {
            authorizationService.assertCanViewPatientHistory(patientId, user);
        } else {
            throw new BusinessException("FORBIDDEN", "Only patients or treating doctors can upload medical documents.");
        }

        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Documents must be 15MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("INVALID_FILE_TYPE", "Only JPEG, PNG, WEBP images or PDF documents are allowed");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String key = "medical-documents/" + patientId + "/" + UUID.randomUUID() + extension;
        s3StorageService.uploadPrivate(key, file);

        MedicalDocument document = MedicalDocument.builder()
                .patientId(patientId)
                .uploadedByUserId(user.getId())
                .documentType(documentType)
                .s3Key(key)
                .originalFilename(originalName)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .relatedAppointmentId(relatedAppointmentId)
                .relatedConsultationId(relatedConsultationId)
                .build();
        document = documentRepository.save(document);

        return MedicalDocumentResponse.from(document, presignedUrl(document));
    }

    public List<MedicalDocumentResponse> listForPatient(UUID patientId, User user) {
        authorizationService.assertCanViewPatientHistory(patientId, user);
        return documentRepository.findByPatientIdOrderByUploadedAtDesc(patientId).stream()
                .map(d -> MedicalDocumentResponse.from(d, presignedUrl(d)))
                .toList();
    }

    public MedicalDocumentResponse getById(UUID id, User user) {
        MedicalDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalDocument", id.toString()));
        authorizationService.assertCanViewPatientHistory(document.getPatientId(), user);
        return MedicalDocumentResponse.from(document, presignedUrl(document));
    }

    private String presignedUrl(MedicalDocument document) {
        return s3StorageService.generatePresignedUrl(document.getS3Key(), Duration.ofMinutes(presignedUrlTtlMinutes));
    }
}
