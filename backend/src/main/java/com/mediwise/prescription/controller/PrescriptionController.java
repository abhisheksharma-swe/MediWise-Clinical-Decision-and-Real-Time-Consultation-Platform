package com.mediwise.prescription.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.prescription.dto.CreatePrescriptionRequest;
import com.mediwise.prescription.dto.PrescriptionResponse;
import com.mediwise.prescription.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Prescriptions", description = "Structured medicine prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping("/api/v1/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Doctor creates a structured prescription for a consultation")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(
            @PathVariable UUID consultationId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(prescriptionService.create(consultationId, user, request)));
    }

    @GetMapping("/api/v1/prescriptions/{id}")
    @Operation(summary = "View a prescription")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(prescriptionService.getById(id, user)));
    }

    @GetMapping("/api/v1/prescriptions/me")
    @Operation(summary = "Get the authenticated patient's own prescriptions")
    public ResponseEntity<ApiResponse<PagedResponse<PrescriptionResponse>>> getMyPrescriptions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(prescriptionService.getMyPrescriptions(user, page, size))));
    }

    @GetMapping("/api/v1/prescriptions/patient/{patientId}")
    @Operation(summary = "List a patient's prescriptions")
    public ResponseEntity<ApiResponse<PagedResponse<PrescriptionResponse>>> getForPatient(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(prescriptionService.getForPatient(patientId, user, page, size))));
    }
}
