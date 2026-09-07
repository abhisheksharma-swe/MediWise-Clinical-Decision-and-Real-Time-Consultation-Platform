package com.mediwise.analytics.service;

import com.mediwise.analytics.dto.AppointmentAnalyticsResponse;
import com.mediwise.analytics.dto.DashboardStatsResponse;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.notification.repository.NotificationRepository;
import com.mediwise.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;

    @Cacheable(value = "analytics", key = "'dashboard'")
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        long total       = appointmentRepository.count();
        long pending     = appointmentRepository.countByStatus(com.mediwise.appointment.model.Appointment.AppointmentStatus.PENDING);
        long confirmed   = appointmentRepository.countByStatus(com.mediwise.appointment.model.Appointment.AppointmentStatus.CONFIRMED);
        long completed   = appointmentRepository.countByStatus(com.mediwise.appointment.model.Appointment.AppointmentStatus.COMPLETED);
        long cancelled   = appointmentRepository.countByStatus(com.mediwise.appointment.model.Appointment.AppointmentStatus.CANCELLED);
        long totalDocs   = doctorRepository.count();
        BigDecimal revenue = paymentRepository.sumSuccessfulPayments();

        return DashboardStatsResponse.builder()
                .totalAppointments(total)
                .pendingAppointments(pending)
                .confirmedAppointments(confirmed)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .totalDoctors(totalDocs)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .revenueThisMonth(paymentRepository.sumSuccessfulPaymentsSince(
                        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant()) != null
                        ? paymentRepository.sumSuccessfulPaymentsSince(
                        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant()) : BigDecimal.ZERO)
                .build();
    }

    @Transactional(readOnly = true)
    public AppointmentAnalyticsResponse getAppointmentAnalytics(LocalDate from, LocalDate to, UUID doctorId) {
        Instant startInst = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInst = to.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        long total     = appointmentRepository.countByDoctorIdAndCreatedAtBetween(doctorId, startInst, endInst);
        long completed = appointmentRepository.countByDoctorIdAndStatusAndCreatedAtBetween(doctorId, com.mediwise.appointment.model.Appointment.AppointmentStatus.COMPLETED, startInst, endInst);
        long cancelled = appointmentRepository.countByDoctorIdAndStatusAndCreatedAtBetween(doctorId, com.mediwise.appointment.model.Appointment.AppointmentStatus.CANCELLED, startInst, endInst);
        long noShow    = appointmentRepository.countByDoctorIdAndStatusAndCreatedAtBetween(doctorId, com.mediwise.appointment.model.Appointment.AppointmentStatus.NO_SHOW, startInst, endInst);

        BigDecimal revenue = paymentRepository.sumByDoctorIdAndDateRange(doctorId, startInst, endInst);

        // Daily counts — iterate dates
        Map<String, Long> dailyCounts = new HashMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Instant dStart = cursor.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dEnd = cursor.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
            long count = appointmentRepository.countByDoctorIdAndCreatedAtBetween(doctorId, dStart, dEnd);
            if (count > 0) dailyCounts.put(cursor.toString(), count);
            cursor = cursor.plusDays(1);
        }

        return AppointmentAnalyticsResponse.builder()
                .from(from)
                .to(to)
                .totalCount(total)
                .completedCount(completed)
                .cancelledCount(cancelled)
                .noShowCount(noShow)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .dailyCounts(dailyCounts)
                .topDoctors(List.of()) // populated separately via JPQL in future iteration
                .build();
    }

    public UUID resolveDoctorIdForRequest(User user, UUID requestedDoctorId) {
        if (user.getRole() == User.Role.ADMIN) {
            return requestedDoctorId; // admin may query any doctor, or null for all
        }

        // DOCTOR: always scoped to their own doctorId
        UUID ownDoctorId = doctorRepository.findByUserId(user.getId())
                .map(d -> d.getId())
                .orElseThrow(() -> new UnauthorizedException("No doctor profile found for this user."));

        if (requestedDoctorId != null && !requestedDoctorId.equals(ownDoctorId)) {
            throw new UnauthorizedException("You may only view analytics for your own appointments.");
        }

        return ownDoctorId;
    }
}