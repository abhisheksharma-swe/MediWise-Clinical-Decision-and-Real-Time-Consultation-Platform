package com.mediwise.profile.controller;

import com.mediwise.auth.model.User;
import com.mediwise.auth.repository.UserRepository;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.profile.dto.UpdateProfileRequest;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Patient-only — mirrors DoctorController's /me endpoints being doctor-only. Without this,
 * a doctor account calling this (e.g. from a role-unaware UI element) would silently get a
 * stray PatientProfile row auto-created for their own user id (see
 * ProfileService.getOrCreateProfile's find-or-create fallback), and any later attempt to use
 * that id anywhere patient-only logic checks role (like AI symptom triage) would 401.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Profile", description = "Patient profile management and image upload")
public class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<PatientProfile>> getProfile(
            @AuthenticationPrincipal User user,
            Authentication authentication) {
        User currentUser = resolveUser(user, authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ApiResponse.success(profileService.getOrCreateProfile(currentUser)));
    }

    @PutMapping
    @Operation(summary = "Update profile details")
    public ResponseEntity<ApiResponse<PatientProfile>> updateProfile(
            @AuthenticationPrincipal User user,
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        User currentUser = resolveUser(user, authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ApiResponse.success(profileService.updateProfile(currentUser, request)));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload profile image to S3")
    public ResponseEntity<ApiResponse<String>> uploadImage(
            @AuthenticationPrincipal User user,
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        User currentUser = resolveUser(user, authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ApiResponse.success(profileService.uploadProfileImage(currentUser, file)));
    }

    private User resolveUser(User user, Authentication authentication) {
        if (user != null && user.getId() != null) {
            return user;
        }
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof User principalUser) {
                return principalUser;
            }
            if (authentication.getName() != null) {
                try {
                    UUID userId = UUID.fromString(authentication.getName());
                    return userRepository.findById(userId).orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }
}
