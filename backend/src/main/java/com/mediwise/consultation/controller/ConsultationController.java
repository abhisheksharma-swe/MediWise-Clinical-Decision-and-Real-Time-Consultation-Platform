package com.mediwise.consultation.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.consultation.dto.ConsultationResponse;
import com.mediwise.consultation.dto.UpdateConsultationRequest;
import com.mediwise.consultation.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
@Tag(name = "Consultations", description = "Structured clinical record for an appointment")
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping("/{appointmentId}")
    @Operation(summary = "Get the consultation record for an appointment")
    public ResponseEntity<ApiResponse<ConsultationResponse>> getForAppointment(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(consultationService.getForAppointment(appointmentId, user)));
    }

    @PutMapping("/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Doctor records/updates the consultation's clinical content")
    public ResponseEntity<ApiResponse<ConsultationResponse>> upsert(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateConsultationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(consultationService.upsert(appointmentId, user, request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated patient's own consultation history")
    public ResponseEntity<ApiResponse<PagedResponse<ConsultationResponse>>> getMyHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(consultationService.getMyHistory(user, page, size))));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "List a patient's consultation history")
    public ResponseEntity<ApiResponse<PagedResponse<ConsultationResponse>>> getPatientHistory(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(consultationService.getPatientHistory(patientId, user, page, size))));
    }
}
