package com.mediwise.analytics.controller;

import com.mediwise.analytics.dto.DashboardStatsResponse;
import com.mediwise.analytics.dto.AppointmentAnalyticsResponse;
import com.mediwise.analytics.service.AnalyticsService;
import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Dashboard and reporting endpoints for doctors and admins")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Get dashboard overview stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDashboardStats()));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Get appointment analytics for a date range")
    public ResponseEntity<ApiResponse<AppointmentAnalyticsResponse>> getAppointmentAnalytics(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorId) {
        UUID effectiveDoctorId = analyticsService.resolveDoctorIdForRequest(user, doctorId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getAppointmentAnalytics(from, to, effectiveDoctorId)));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get revenue breakdown (Admin only)")
    public ResponseEntity<ApiResponse<AppointmentAnalyticsResponse>> getRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getAppointmentAnalytics(from, to, null)));
    }
}