package com.mediwise.admin.controller;

import com.mediwise.admin.dto.AdminStatsResponse;
import com.mediwise.admin.dto.DoctorApprovalRequest;
import com.mediwise.admin.dto.UserRoleUpdateRequest;
import com.mediwise.admin.dto.UserStatusUpdateRequest;
import com.mediwise.admin.dto.UserSummaryResponse;
import com.mediwise.admin.service.AdminService;
import com.mediwise.appointment.dto.AppointmentResponse;
import com.mediwise.auth.model.User;
import com.mediwise.common.audit.AuditLogResponse;
import com.mediwise.common.audit.AuditLogService;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.doctor.dto.DoctorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "Endpoints for managing users, doctor approvals, and platform governance")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;

    @GetMapping("/stats")
    @Operation(summary = "Platform-wide analytics & metrics for admin dashboard")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getStats()));
    }

    @GetMapping("/users")
    @Operation(summary = "List/search all users with role filtering")
    public ResponseEntity<ApiResponse<PagedResponse<UserSummaryResponse>>> getUsers(
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(adminService.getUsers(role, search, page, size))));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserById(id)));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Suspend or activate user account")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateUserStatus(id, request.getActive()), "User status updated successfully"));
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Admin only: Update user role (PATIENT / DOCTOR / ADMIN)")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserRoleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateUserRole(id, request.getRole()), "User role updated successfully"));
    }

    @GetMapping("/doctors")
    @Operation(summary = "List doctors with verification status filtering")
    public ResponseEntity<ApiResponse<PagedResponse<DoctorResponse>>> getDoctors(
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(adminService.getDoctors(verified, specialty, search, page, size))));
    }

    @PatchMapping("/doctors/{id}/verify")
    @Operation(summary = "Approve and verify doctor credentials")
    public ResponseEntity<ApiResponse<DoctorResponse>> verifyDoctor(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.verifyDoctor(id, true), "Doctor verified successfully"));
    }

    @PatchMapping("/doctors/{id}/reject")
    @Operation(summary = "Reject doctor registration")
    public ResponseEntity<ApiResponse<Void>> rejectDoctor(
            @PathVariable UUID id,
            @RequestBody(required = false) DoctorApprovalRequest request) {
        String reason = request != null ? request.getReason() : "Did not meet verification criteria";
        adminService.rejectDoctor(id, reason);
        return ResponseEntity.ok(ApiResponse.message("Doctor registration rejected"));
    }

    @GetMapping("/appointments")
    @Operation(summary = "Admin oversight: view all appointments across platform")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getAllAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(adminService.getAllAppointments(status, page, size))));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Paginated, filterable audit log (actor/date-range/outcome)")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(auditLogService.search(actorId, outcome, start, end, page, size))));
    }
}
