package com.mediwise.payment.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.payment.dto.InitiatePaymentRequest;
import com.mediwise.payment.dto.PaymentResponse;
import com.mediwise.payment.dto.VerifyPaymentRequest;
import com.mediwise.payment.service.RazorpayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay payment initiation, verification and webhook")
public class PaymentController {

    private final RazorpayService razorpayService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate Razorpay order")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(razorpayService.initiatePayment(request, user.getId())));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify payment after Razorpay checkout")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                razorpayService.verifyAndConfirmPayment(request, user.getId())));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay webhook (HMAC verified, no JWT auth) — server-side reconciliation")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        // Verify the request actually came from Razorpay (not a spoofed call)
        if (!razorpayService.verifyWebhookSignature(payload, signature)) {
            log.warn("Razorpay webhook signature mismatch — ignoring");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Process: if payment.captured event, confirm the appointment
        razorpayService.processWebhookEvent(payload);
        return ResponseEntity.ok().build();
    }
}
