package com.mediwise.auth.controller;

import com.mediwise.auth.dto.*;
import com.mediwise.auth.model.User;
import com.mediwise.auth.service.AuthService;
import com.mediwise.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Onboarding", description = "Endpoints for MediWise login, signup, password reset, and session initialization")
public class AuthController {

    private final AuthService authService;

    @GetMapping("/config")
    @Operation(summary = "Get MediWise system configuration and health status for Splash screen")
    public ResponseEntity<ApiResponse<AppConfigResponse>> getAppConfig() {
        return ResponseEntity.ok(ApiResponse.success(authService.getAppConfig()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new MediWise user account (Patient/Doctor/Admin)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request), "Registration successful"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with credentials (email/phone + password) or Firebase token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(refreshToken), "Token refreshed"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate forgot password request")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.message("If an account exists, a reset code has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set new password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.message("Password has been reset successfully. Please log in."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user information")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getCurrentUser(
            @AuthenticationPrincipal User currentUser,
            Authentication authentication
    ) {
        UUID userId = null;
        if (currentUser != null && currentUser.getId() != null) {
            userId = currentUser.getId();
        } else if (authentication != null && authentication.getName() != null) {
            try {
                userId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser(userId)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (currentUser == null || currentUser.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.message("Password changed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate user access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && !authHeader.isBlank()) {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            authService.logout(token);
        }
        return ResponseEntity.ok(ApiResponse.message("Logged out successfully"));
    }
}
