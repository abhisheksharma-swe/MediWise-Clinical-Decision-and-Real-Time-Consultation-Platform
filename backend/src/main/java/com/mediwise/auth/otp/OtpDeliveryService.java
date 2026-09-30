package com.mediwise.auth.otp;

import com.mediwise.auth.model.User;

/**
 * Delivers a one-time password to a user through whatever channel a real
 * deployment configures (email, SMS, etc.). {@link LoggingOtpDeliveryService}
 * is the only implementation today — it preserves the exact previous
 * behavior (log server-side, no real delivery) — but callers depend on this
 * interface rather than a concrete log call, so a real provider (SES, SNS,
 * Twilio, ...) can be dropped in as a second {@code @Service} implementation
 * without touching {@code AuthService}.
 */
public interface OtpDeliveryService {
    void deliverPasswordResetOtp(User user, String otp, long validityMinutes);
}
