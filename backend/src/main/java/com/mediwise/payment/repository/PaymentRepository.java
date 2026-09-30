package com.mediwise.payment.repository;

import com.mediwise.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByAppointmentIdAndStatus(UUID appointmentId, Payment.PaymentStatus status);
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    Optional<Payment> findByAppointmentId(UUID appointmentId);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPayments();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.createdAt >= :start")
    BigDecimal sumSuccessfulPaymentsSince(@Param("start") Instant start);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.appointmentId IN (SELECT a.id FROM Appointment a WHERE (:doctorId IS NULL OR a.doctorId = :doctorId)) AND p.createdAt BETWEEN :start AND :end")
    BigDecimal sumByDoctorIdAndDateRange(@Param("doctorId") UUID doctorId, @Param("start") Instant start, @Param("end") Instant end);

    long countByStatus(Payment.PaymentStatus status);
}