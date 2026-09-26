package com.mediwise.medicalrecord.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.medicalrecord.dto.*;
import com.mediwise.medicalrecord.service.MedicalRecordService;
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
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
@Tag(name = "Medical Records", description = "Patient-owned conditions, allergies and medications")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get the authenticated patient's own medical record")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> getMyRecord(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(medicalRecordService.getMyRecord(user)));
    }

    @PostMapping("/me/conditions")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Patient adds a self-reported condition to their own record")
    public ResponseEntity<ApiResponse<ConditionResponse>> addMyCondition(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateConditionRequest request) {
        UUID patientId = medicalRecordService.resolveOwnPatientId(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addCondition(patientId, user, request)));
    }

    @PostMapping("/me/allergies")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Patient adds a self-reported allergy to their own record")
    public ResponseEntity<ApiResponse<AllergyResponse>> addMyAllergy(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateAllergyRequest request) {
        UUID patientId = medicalRecordService.resolveOwnPatientId(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addAllergy(patientId, user, request)));
    }

    @PostMapping("/me/medications")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Patient adds a self-reported medication to their own record")
    public ResponseEntity<ApiResponse<MedicationResponse>> addMyMedication(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateMedicationRequest request) {
        UUID patientId = medicalRecordService.resolveOwnPatientId(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addMedication(patientId, user, request)));
    }

    @GetMapping("/{patientId}")
    @Operation(summary = "Get a patient's aggregated medical record")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> getRecord(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(medicalRecordService.getRecord(patientId, user)));
    }

    @PostMapping("/{patientId}/conditions")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a condition (self-reported by patient or doctor-authored)")
    public ResponseEntity<ApiResponse<ConditionResponse>> addCondition(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateConditionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addCondition(patientId, user, request)));
    }

    @PostMapping("/{patientId}/allergies")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an allergy (self-reported by patient or doctor-authored)")
    public ResponseEntity<ApiResponse<AllergyResponse>> addAllergy(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateAllergyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addAllergy(patientId, user, request)));
    }

    @PostMapping("/{patientId}/medications")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a medication (self-reported by patient or doctor-authored)")
    public ResponseEntity<ApiResponse<MedicationResponse>> addMedication(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateMedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(medicalRecordService.addMedication(patientId, user, request)));
    }

    @PatchMapping("/conditions/{id}")
    @Operation(summary = "Update a condition's status (e.g. mark resolved)")
    public ResponseEntity<ApiResponse<ConditionResponse>> updateConditionStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateConditionStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(medicalRecordService.updateConditionStatus(id, user, request)));
    }
}
