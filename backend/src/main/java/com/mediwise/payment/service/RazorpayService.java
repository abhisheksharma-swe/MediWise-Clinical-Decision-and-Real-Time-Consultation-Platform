package com.mediwise.payment.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.PaymentException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.payment.dto.InitiatePaymentRequest;
import com.mediwise.payment.dto.PaymentResponse;
import com.mediwise.payment.dto.VerifyPaymentRequest;
import com.mediwise.payment.model.Payment;
import com.mediwise.payment.repository.PaymentRepository;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayOrderGateway razorpayOrderGateway;
    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Value("${application.razorpay.key-secret}")
    private String keySecret;

    @Value("${application.razorpay.webhook-secret}")
    private String webhookSecret;

    private static final int MAX_RETRIES = 3;

    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request, UUID requestingUserId) {

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", request.getAppointmentId().toString()));

        // SECURITY: only the patient who owns this appointment can pay for it.
        var ownPatientId = patientProfileRepository.findByUserId(requestingUserId)
                .map(p -> p.getId())
                .orElse(null);
        if (ownPatientId == null || !appointment.getPatientId().equals(ownPatientId)) {
            throw new UnauthorizedException("You do not have permission to pay for this appointment.");
        }

        if (appointment.getStatus() == Appointment.AppointmentStatus.CONFIRMED) {
            throw new BusinessException("ALREADY_PAID", "This appointment has already been paid for.");
        }
        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            throw new BusinessException("APPOINTMENT_CANCELLED", "Cannot pay for a cancelled appointment.");
        }

        Doctor doctor = doctorRepository.findById(appointment.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", appointment.getDoctorId().toString()));

        BigDecimal authorizedAmount = doctor.getConsultationFee();
        if (authorizedAmount == null || authorizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_FEE", "Doctor has not set a valid consultation fee.");
        }

        Payment existingPayment = paymentRepository
                .findByAppointmentIdAndStatus(request.getAppointmentId(), Payment.PaymentStatus.INITIATED)
                .orElse(null);

        if (existingPayment != null) {
            if (existingPayment.getRetryCount() >= MAX_RETRIES) {
                throw new PaymentException("Maximum payment retries exceeded. Please contact support.");
            }
            existingPayment.setRetryCount(existingPayment.getRetryCount() + 1);
            paymentRepository.save(existingPayment);
            log.info("Reusing existing Razorpay order {} for appointment {}",
                    existingPayment.getGatewayOrderId(), request.getAppointmentId());
            return PaymentResponse.from(existingPayment);
        }

        try {
            String razorpayOrderId = razorpayOrderGateway.createOrder(
                    request.getAppointmentId(), authorizedAmount);

            Payment payment = Payment.builder()
                    .appointmentId(request.getAppointmentId())
                    .patientId(appointment.getPatientId())
                    .amount(authorizedAmount)
                    .currency("INR")
                    .status(Payment.PaymentStatus.INITIATED)
                    .gateway("RAZORPAY")
                    .gatewayOrderId(razorpayOrderId)
                    .build();

            payment = paymentRepository.save(payment);
            log.info("Payment {} initiated | amount=₹{} | Razorpay order={}",
                    payment.getId(), authorizedAmount, payment.getGatewayOrderId());

            return PaymentResponse.from(payment);

        } catch (RazorpayException e) {
            throw new PaymentException("Failed to create payment order: " + e.getMessage());
        } catch (Exception e) {
            throw new PaymentException("Unexpected error creating payment order: " + e.getMessage());
        }
    }

    // verifyAndConfirmPayment, processWebhookEvent, verifyWebhookSignature, computeHmac — all unchanged from what you pasted
    @Transactional
    public PaymentResponse verifyAndConfirmPayment(VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentException("Payment order not found."));

        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            log.info("Payment {} already verified — returning cached response", payment.getId());
            return PaymentResponse.from(payment);
        }

        String expectedSignature = computeHmac(
                request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(),
                keySecret);

        if (!expectedSignature.equals(request.getRazorpaySignature())) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setLastError("Signature mismatch — possible tampering attempt");
            paymentRepository.save(payment);
            log.error("Payment signature mismatch for order {} — POSSIBLE TAMPERING",
                    request.getRazorpayOrderId());
            throw new PaymentException("Payment verification failed. Please contact support.");
        }

        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(request.getRazorpayPaymentId());
        payment.setGatewaySignature(request.getRazorpaySignature());
        paymentRepository.save(payment);

        appointmentRepository.findById(payment.getAppointmentId()).ifPresent(appointment -> {
            if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
                appointmentRepository.save(appointment);
                log.info("Appointment {} CONFIRMED after payment {}", appointment.getId(), payment.getId());
            }
        });

        log.info("Payment {} verified successfully for ₹{}", payment.getId(), payment.getAmount());
        return PaymentResponse.from(payment);
    }

    @Transactional
    public void processWebhookEvent(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.optString("event");

            if (!"payment.captured".equals(eventType)) {
                log.debug("Ignoring webhook event: {}", eventType);
                return;
            }

            JSONObject paymentEntity = event
                    .getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String razorpayOrderId = paymentEntity.getString("order_id");
            String razorpayPaymentId = paymentEntity.getString("id");

            paymentRepository.findByGatewayOrderId(razorpayOrderId).ifPresent(payment -> {
                if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
                    payment.setStatus(Payment.PaymentStatus.SUCCESS);
                    payment.setGatewayPaymentId(razorpayPaymentId);
                    paymentRepository.save(payment);

                    appointmentRepository.findById(payment.getAppointmentId()).ifPresent(appointment -> {
                        if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                            appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
                            appointmentRepository.save(appointment);
                            log.info("Appointment {} confirmed via webhook", appointment.getId());
                        }
                    });
                }
            });

        } catch (Exception e) {
            log.error("Failed to process webhook payload: {}", e.getMessage(), e);
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        String computed = computeHmac(payload, webhookSecret);
        return computed.equals(signature);
    }

    private String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new PaymentException("HMAC computation failed: " + e.getMessage());
        }
    }
}