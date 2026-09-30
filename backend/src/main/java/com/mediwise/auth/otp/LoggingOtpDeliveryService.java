package com.mediwise.auth.otp;

import com.mediwise.auth.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link OtpDeliveryService}: logs the event server-side only, exactly
 * matching the previous inline behavior in {@code AuthService}. Never logs the
 * OTP itself — only that one was generated — so this remains safe to run in
 * production as-is until a real email/SMS provider is configured.
 */
@Slf4j
@Service
public class LoggingOtpDeliveryService implements OtpDeliveryService {

    @Override
    public void deliverPasswordResetOtp(User user, String otp, long validityMinutes) {
        log.info("Password reset OTP generated for user: {} (valid {} min) — no delivery provider configured, " +
                "OTP was not sent to the user. Configure a real OtpDeliveryService bean for production use.",
                user.getEmail(), validityMinutes);
    }
}
