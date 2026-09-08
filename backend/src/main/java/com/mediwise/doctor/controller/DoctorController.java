package com.mediwise.doctor.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.doctor.dto.DoctorResponse;
import com.mediwise.doctor.dto.UpdateDoctorProfileRequest;
import com.mediwise.doctor.service.DoctorService;
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
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Doctor discovery, details and favorites")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    @Operation(summary = "List/search doctors with filters")
    public ResponseEntity<ApiResponse<PagedResponse<DoctorResponse>>> getDoctors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "rating") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                doctorService.getDoctors(search, specialty, sortBy, page, size)));
    }

    // ── Doctor-only: view/update own professional profile ──────────────────────

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Get the logged-in doctor's own professional profile")
    public ResponseEntity<ApiResponse<DoctorResponse>> getMyProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.getMyProfile(currentUser)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Update the logged-in doctor's own professional profile")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateMyProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.updateMyProfile(currentUser, request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor by ID")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctor(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.getDoctorById(id, currentUser)));
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Toggle doctor favorite")
    public ResponseEntity<ApiResponse<Void>> toggleFavorite(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        doctorService.toggleFavorite(currentUser, id);
        return ResponseEntity.ok(ApiResponse.message("Favorite updated"));
    }

    @GetMapping("/favorites")
    @Operation(summary = "Get patient's favorite doctors")
    public ResponseEntity<ApiResponse<PagedResponse<DoctorResponse>>> getFavorites(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(doctorService.getFavorites(currentUser, page, size))));
    }
}
