package com.mediwise.followup.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.followup.dto.CreateFollowUpRequest;
import com.mediwise.followup.dto.FollowUpResponse;
import com.mediwise.followup.service.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Follow-ups", description = "Doctor-recommended follow-up visits")
public class FollowUpController {

    private final FollowUpService followUpService;

    @PostMapping("/api/v1/consultations/{consultationId}/follow-ups")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Doctor creates a follow-up recommendation")
    public ResponseEntity<ApiResponse<FollowUpResponse>> create(
            @PathVariable UUID consultationId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateFollowUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(followUpService.create(consultationId, user, request)));
    }

    @GetMapping("/api/v1/follow-ups/me")
    @Operation(summary = "Get the authenticated patient's own follow-ups")
    public ResponseEntity<ApiResponse<List<FollowUpResponse>>> getMyFollowUps(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(followUpService.getMyFollowUps(user)));
    }

    @GetMapping("/api/v1/follow-ups/patient/{patientId}")
    @Operation(summary = "List a patient's follow-ups")
    public ResponseEntity<ApiResponse<List<FollowUpResponse>>> getForPatient(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(followUpService.getForPatient(patientId, user)));
    }

    @PatchMapping("/api/v1/follow-ups/{id}/dismiss")
    @Operation(summary = "Patient dismisses a suggested follow-up")
    public ResponseEntity<ApiResponse<FollowUpResponse>> dismiss(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(followUpService.dismiss(id, user)));
    }
}
