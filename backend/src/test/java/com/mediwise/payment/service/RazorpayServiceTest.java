package com.mediwise.payment.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.PaymentException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.payment.dto.InitiatePaymentRequest;
import com.mediwise.payment.dto.PaymentResponse;
import com.mediwise.payment.dto.VerifyPaymentRequest;
import com.mediwise.payment.model.Payment;
import com.mediwise.payment.repository.PaymentRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RazorpayService.
 *
 * KEY DESIGN DECISION:
 * We mock RazorpayOrderGateway (our interface) — NOT the Razorpay SDK directly.
 * The SDK uses public fields (client.orders) which are not mockable with
 * Mockito.
 * This is why we introduced the Adapter Pattern (RazorpayOrderGateway
 * interface).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RazorpayService — Payment Lifecycle Tests")
class RazorpayServiceTest {

        // Our adapter interface — cleanly mockable
        @Mock
        private RazorpayOrderGateway razorpayOrderGateway;
        @Mock
        private PaymentRepository paymentRepository;
        @Mock
        private AppointmentRepository appointmentRepository;
        @Mock
        private DoctorRepository doctorRepository;
        @Mock
        private PatientProfileRepository patientProfileRepository;
        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private RazorpayService razorpayService;

        private static final String TEST_KEY_SECRET = "test_secret_key_12345678901234";
        private static final String TEST_WEBHOOK_SECRET = "test_webhook_secret_abc";

        private UUID patientId;
        private UUID patientProfileId;
        private UUID doctorId;
        private UUID appointmentId;
        private Appointment pendingAppointment;
        private Doctor doctor;

        @BeforeEach
        void setUp() {
                // @Value fields are not injected in unit tests — set them manually
                ReflectionTestUtils.setField(razorpayService, "keySecret", TEST_KEY_SECRET);
                ReflectionTestUtils.setField(razorpayService, "webhookSecret", TEST_WEBHOOK_SECRET);

                patientId = UUID.randomUUID();
                patientProfileId = UUID.randomUUID();
                doctorId = UUID.randomUUID();
                appointmentId = UUID.randomUUID();

                doctor = Doctor.builder()
                        .id(doctorId)
                        .fullName("Dr. Smith")
                        .consultationFee(new BigDecimal("500.00"))
                        .verified(true)
                        .build();

                // patientId (the appointment owner) matches patientProfileId so the
                // ownership check in initiatePayment() passes for the "legit owner" tests
                pendingAppointment = Appointment.builder()
                        .id(appointmentId)
                        .patientId(patientProfileId)
                        .doctorId(doctorId)
                        .slotId(UUID.randomUUID())
                        .status(Appointment.AppointmentStatus.PENDING)
                        .build();
        }

        // Helper — stub the patient owning this appointment (used by initiatePayment tests)
        private void stubOwningPatient() {
                PatientProfile profile = PatientProfile.builder()
                        .id(patientProfileId)
                        .userId(patientId)
                        .build();
                when(patientProfileRepository.findByUserId(patientId))
                        .thenReturn(Optional.of(profile));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 1: Initiate payment — server reads fee, not client
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("initiatePayment: creates Razorpay order using server-authoritative fee")
        void initiatePayment_usesServerFeeNotClientSupplied() throws Exception {
                // Arrange
                stubOwningPatient();

                InitiatePaymentRequest request = new InitiatePaymentRequest();
                request.setAppointmentId(appointmentId);
                // Note: NO amount field — the whole security point of the redesign

                when(appointmentRepository.findById(appointmentId))
                        .thenReturn(Optional.of(pendingAppointment));
                when(doctorRepository.findById(doctorId))
                        .thenReturn(Optional.of(doctor));
                when(paymentRepository.findByAppointmentIdAndStatus(appointmentId, Payment.PaymentStatus.INITIATED))
                        .thenReturn(Optional.empty());

                // Gateway returns a fake Razorpay order ID (no real HTTP call)
                when(razorpayOrderGateway.createOrder(eq(appointmentId), eq(new BigDecimal("500.00"))))
                        .thenReturn("order_testABC123");

                Payment savedPayment = Payment.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .patientId(patientProfileId)
                        .amount(new BigDecimal("500.00"))
                        .currency("INR")
                        .status(Payment.PaymentStatus.INITIATED)
                        .gatewayOrderId("order_testABC123")
                        .build();
                when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

                // Act
                PaymentResponse response = razorpayService.initiatePayment(request, patientId);

                // Assert
                assertThat(response.getGatewayOrderId()).isEqualTo("order_testABC123");
                // The amount in the response must be ₹500 — from the doctor entity, not the
                // request
                assertThat(response.getAmount()).isEqualByComparingTo("500.00");
                assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.INITIATED);

                // Verify gateway was called with the doctor's fee (₹500), not any
                // client-supplied value
                verify(razorpayOrderGateway).createOrder(appointmentId, new BigDecimal("500.00"));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 2: Reject double-payment attempt
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("initiatePayment: throws BusinessException if appointment already CONFIRMED")
        void initiatePayment_rejectsAlreadyConfirmedAppointment() {
                stubOwningPatient();

                pendingAppointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
                when(appointmentRepository.findById(appointmentId))
                        .thenReturn(Optional.of(pendingAppointment));

                InitiatePaymentRequest request = new InitiatePaymentRequest();
                request.setAppointmentId(appointmentId);

                assertThatThrownBy(() -> razorpayService.initiatePayment(request, patientId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("already been paid");

                // Gateway must never be called
                verifyNoInteractions(razorpayOrderGateway);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 3: THE CRITICAL FLOW — verify payment and confirm appointment
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("verifyAndConfirmPayment: marks Payment SUCCESS and Appointment CONFIRMED")
        void verifyAndConfirmPayment_confirmsAppointmentOnSuccess() throws Exception {
                String orderId = "order_testABC123";
                String paymentId = "pay_test456";
                // Compute the exact HMAC the backend will also compute — must match
                String signature = computeValidSignature(orderId, paymentId);

                VerifyPaymentRequest request = new VerifyPaymentRequest();
                request.setRazorpayOrderId(orderId);
                request.setRazorpayPaymentId(paymentId);
                request.setRazorpaySignature(signature);

                Payment initiatedPayment = Payment.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .patientId(patientProfileId)
                        .amount(new BigDecimal("500.00"))
                        .status(Payment.PaymentStatus.INITIATED)
                        .gatewayOrderId(orderId)
                        .build();

                stubOwningPatient();
                when(paymentRepository.findByGatewayOrderId(orderId))
                        .thenReturn(Optional.of(initiatedPayment));
                when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(appointmentRepository.findById(appointmentId))
                        .thenReturn(Optional.of(pendingAppointment));
                when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // Act
                PaymentResponse response = razorpayService.verifyAndConfirmPayment(request, patientId);

                // Assert payment is SUCCESS
                assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);

                // THE CRITICAL ASSERTION: Appointment must have been saved with CONFIRMED
                // status
                verify(appointmentRepository)
                        .save(argThat(apt -> apt.getStatus() == Appointment.AppointmentStatus.CONFIRMED));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 4: Reject tampered/forged signature
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("verifyAndConfirmPayment: throws PaymentException on tampered signature")
        void verifyAndConfirmPayment_rejectsTamperedSignature() {
                VerifyPaymentRequest request = new VerifyPaymentRequest();
                request.setRazorpayOrderId("order_real");
                request.setRazorpayPaymentId("pay_real");
                request.setRazorpaySignature("i_am_a_hacker_this_is_fake_sig");

                Payment initiatedPayment = Payment.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .patientId(patientProfileId)
                        .status(Payment.PaymentStatus.INITIATED)
                        .gatewayOrderId("order_real")
                        .build();

                stubOwningPatient();
                when(paymentRepository.findByGatewayOrderId("order_real"))
                        .thenReturn(Optional.of(initiatedPayment));
                when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // Act & Assert
                assertThatThrownBy(() -> razorpayService.verifyAndConfirmPayment(request, patientId))
                        .isInstanceOf(PaymentException.class)
                        .hasMessageContaining("verification failed");

                // Appointment must NEVER be confirmed
                verify(appointmentRepository, never()).save(any());

                // Payment must be recorded as FAILED (audit trail for fraud investigation)
                verify(paymentRepository).save(argThat(p -> p.getStatus() == Payment.PaymentStatus.FAILED));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST: A user who doesn't own this payment cannot verify it (IDOR guard)
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("verifyAndConfirmPayment: rejects a caller who does not own the payment's appointment")
        void verifyAndConfirmPayment_rejectsNonOwner() {
                VerifyPaymentRequest request = new VerifyPaymentRequest();
                request.setRazorpayOrderId("order_someone_elses");
                request.setRazorpayPaymentId("pay_123");
                request.setRazorpaySignature("whatever");

                Payment someoneElsesPayment = Payment.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(UUID.randomUUID())
                        .patientId(UUID.randomUUID()) // NOT patientProfileId
                        .status(Payment.PaymentStatus.INITIATED)
                        .gatewayOrderId("order_someone_elses")
                        .build();

                stubOwningPatient(); // resolves patientId -> patientProfileId, which won't match
                when(paymentRepository.findByGatewayOrderId("order_someone_elses"))
                        .thenReturn(Optional.of(someoneElsesPayment));

                assertThatThrownBy(() -> razorpayService.verifyAndConfirmPayment(request, patientId))
                        .isInstanceOf(UnauthorizedException.class);

                verify(paymentRepository, never()).save(any());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // TEST 5: Idempotent retry — user comes back after app crash
        // ─────────────────────────────────────────────────────────────────────────
        @Test
        @DisplayName("initiatePayment: reuses existing Razorpay order on retry instead of creating new one")
        void initiatePayment_returnsExistingOrderOnRetry() throws Exception {
                stubOwningPatient();

                InitiatePaymentRequest request = new InitiatePaymentRequest();
                request.setAppointmentId(appointmentId);

                when(appointmentRepository.findById(appointmentId))
                        .thenReturn(Optional.of(pendingAppointment));
                when(doctorRepository.findById(doctorId))
                        .thenReturn(Optional.of(doctor));

                // Simulate: user already started payment, Razorpay order already created
                Payment existingPayment = Payment.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .amount(new BigDecimal("500.00"))
                        .status(Payment.PaymentStatus.INITIATED)
                        .gatewayOrderId("order_existing_XYZ")
                        .retryCount(1)
                        .build();
                when(paymentRepository.findByAppointmentIdAndStatus(appointmentId, Payment.PaymentStatus.INITIATED))
                        .thenReturn(Optional.of(existingPayment));
                when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // Act
                PaymentResponse response = razorpayService.initiatePayment(request, patientId);

                // Must return the SAME order — no new Razorpay API call
                assertThat(response.getGatewayOrderId()).isEqualTo("order_existing_XYZ");
                assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.INITIATED);

                // Gateway must NOT have been called (no duplicate order)
                verifyNoInteractions(razorpayOrderGateway);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Helper — compute the HMAC signature exactly as Razorpay produces it
        // ─────────────────────────────────────────────────────────────────────────
        private String computeValidSignature(String orderId, String paymentId) throws Exception {
                String data = orderId + "|" + paymentId;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(
                        TEST_KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(
                        mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        }
}