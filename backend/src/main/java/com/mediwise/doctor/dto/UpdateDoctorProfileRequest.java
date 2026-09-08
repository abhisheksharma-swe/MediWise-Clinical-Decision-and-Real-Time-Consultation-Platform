package com.mediwise.doctor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for a doctor updating their own professional profile.
 * Deliberately excludes fields a doctor must not self-edit: {@code verified}
 * (admin-controlled trust signal), {@code licenseNumber} (compliance data),
 * and {@code avgRating}/{@code totalReviews} (computed from patient reviews).
 */
@Data
public class UpdateDoctorProfileRequest {
    private String fullName;
    private String specialty;
    private String bio;

    @Min(value = 0, message = "Experience cannot be negative")
    private Integer experienceYears;

    @DecimalMin(value = "0.0", inclusive = false, message = "Consultation fee must be greater than zero")
    private BigDecimal consultationFee;

    /** Whether the doctor is currently accepting new bookings. */
    private Boolean available;
}
