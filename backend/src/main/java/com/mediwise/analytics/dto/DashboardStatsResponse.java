package com.mediwise.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    private long totalAppointments;
    private long pendingAppointments;
    private long confirmedAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private long totalPatients;
    private long totalDoctors;
    private BigDecimal totalRevenue;
    private BigDecimal revenueThisMonth;
    private double averageRating;
    private long totalReviews;
    private long unreadNotifications;
}
