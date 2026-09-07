package com.mediwise.payment.repository;

import com.mediwise.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByAppointmentIdAndStatus(UUID appointmentId, Payment.PaymentStatus status);
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    Optional<Payment> findByAppointmentId(UUID appointmentId);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS'")
    java.math.BigDecimal sumSuccessfulPayments();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.createdAt >= :start")
    java.math.BigDecimal sumSuccessfulPaymentsSince(@org.springframework.data.repository.query.Param("start") java.time.Instant start);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.appointmentId IN (SELECT a.id FROM Appointment a WHERE (:doctorId IS NULL OR a.doctorId = :doctorId)) AND p.createdAt BETWEEN :start AND :end")
    java.math.BigDecimal sumByDoctorIdAndDateRange(@org.springframework.data.repository.query.Param("doctorId") UUID doctorId, @org.springframework.data.repository.query.Param("start") java.time.Instant start, @org.springframework.data.repository.query.Param("end") java.time.Instant end);

    long countByStatus(Payment.PaymentStatus status);
}