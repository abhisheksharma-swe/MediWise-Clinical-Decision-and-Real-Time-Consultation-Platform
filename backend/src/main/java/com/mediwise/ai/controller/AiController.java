package com.mediwise.ai.controller;

import com.mediwise.ai.dto.AiReportResponse;
import com.mediwise.ai.dto.SymptomLogRequest;
import com.mediwise.ai.service.AiService;
import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI / Clinical Decision", description = "AI-powered symptom triage and clinical report endpoints")
public class AiController {

    private final AiService aiService;

    @PostMapping("/symptom-log")
    @Operation(summary = "Log patient symptoms and receive AI triage result")
    public ResponseEntity<ApiResponse<AiReportResponse>> logSymptoms(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SymptomLogRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiService.analyzeSymptoms(request, user)));
    }

    @GetMapping("/reports/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Get all AI reports for a patient (Doctor/Admin only)")
    public ResponseEntity<ApiResponse<List<AiReportResponse>>> getReports(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(aiService.getReportsForPatient(patientId, user)));
    }

    @GetMapping("/reports/{patientId}/latest")
    @Operation(summary = "Get latest AI report for a patient")
    public ResponseEntity<ApiResponse<AiReportResponse>> getLatestReport(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(aiService.getLatestReport(patientId, user)));
    }
}