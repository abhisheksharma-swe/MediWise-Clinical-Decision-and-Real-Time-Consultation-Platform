package com.mediwise.appointment.controller;

import com.mediwise.appointment.dto.AppointmentResponse;
import com.mediwise.appointment.dto.BookAppointmentRequest;
import com.mediwise.appointment.dto.CancelRequest;
import com.mediwise.appointment.dto.CompleteAppointmentRequest;
import com.mediwise.appointment.service.AppointmentService;
import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Book, view and cancel appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a new appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(appointmentService.bookAppointment(user, request)));
    }

    @GetMapping
    @Operation(summary = "Get my appointments (filter by status)")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getMyAppointments(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(appointmentService.getMyAppointments(user, status, page, size))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment detail")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getAppointmentById(id, user)));
    }

        @GetMapping("/history")
        @Operation(summary = "Get the authenticated patient's completed consultation history")
        public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getMyConsultationHistory(
                        @AuthenticationPrincipal User user,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                UUID patientId = appointmentService.getPatientIdForHistory(user);
                return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                                appointmentService.getConsultationHistory(patientId, user, page, size))));
        }

        @GetMapping("/patient/{patientId}/history")
        @Operation(summary = "Get a patient's completed consultation history for an authorized doctor")
        public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getPatientConsultationHistory(
                        @PathVariable UUID patientId,
                        @AuthenticationPrincipal User user,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(
                                appointmentService.getConsultationHistory(patientId, user, page, size))));
        }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            @RequestBody CancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.cancelAppointment(id, user, request)));
    }

    // ── Doctor-only endpoints ─────────────────────────────────────────────────

    @PatchMapping("/{id}/start")
    @Operation(summary = "Doctor starts the consultation call (CONFIRMED → IN_PROGRESS)")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "Bearer")
    public ResponseEntity<ApiResponse<AppointmentResponse>> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.startConsultation(id, user)));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Doctor completes consultation + saves clinical notes (IN_PROGRESS → COMPLETED)")
    public ResponseEntity<ApiResponse<AppointmentResponse>> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CompleteAppointmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.completeAppointment(id, user, request)));
    }

    @PatchMapping("/{id}/no-show")
    @Operation(summary = "Doctor marks the patient as a no-show (CONFIRMED → NO_SHOW)")
    public ResponseEntity<ApiResponse<AppointmentResponse>> noShow(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.markNoShow(id, user)));
    }

    @GetMapping("/doctor")
    @Operation(summary = "Doctor views their own appointment schedule")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getDoctorAppointments(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(appointmentService.getDoctorAppointments(user, status, page, size))));
    }
}
