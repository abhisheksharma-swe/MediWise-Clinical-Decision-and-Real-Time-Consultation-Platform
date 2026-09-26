package com.mediwise.medicalrecord.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.medicalrecord.dto.MedicalDocumentResponse;
import com.mediwise.medicalrecord.model.MedicalDocument;
import com.mediwise.medicalrecord.service.MedicalDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-documents")
@RequiredArgsConstructor
@Tag(name = "Medical Documents", description = "Patient document vault (lab reports, imaging, discharge summaries)")
public class MedicalDocumentController {

    private final MedicalDocumentService medicalDocumentService;

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a medical document")
    public ResponseEntity<ApiResponse<MedicalDocumentResponse>> upload(
            @AuthenticationPrincipal User user,
            @RequestParam UUID patientId,
            @RequestParam MedicalDocument.DocumentType documentType,
            @RequestParam(required = false) UUID relatedAppointmentId,
            @RequestParam(required = false) UUID relatedConsultationId,
            @RequestParam MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                medicalDocumentService.upload(patientId, user, file, documentType, relatedAppointmentId, relatedConsultationId)));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "List a patient's medical documents")
    public ResponseEntity<ApiResponse<List<MedicalDocumentResponse>>> listForPatient(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(medicalDocumentService.listForPatient(patientId, user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single medical document (fresh presigned URL)")
    public ResponseEntity<ApiResponse<MedicalDocumentResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(medicalDocumentService.getById(id, user)));
    }
}
