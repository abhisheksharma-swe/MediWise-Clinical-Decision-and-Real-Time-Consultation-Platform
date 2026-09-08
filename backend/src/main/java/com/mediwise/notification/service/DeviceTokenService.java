package com.mediwise.notification.service;

import com.mediwise.notification.model.DeviceToken;
import com.mediwise.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable, multi-device FCM token registry backed by PostgreSQL. Replaces the
 * old single-Redis-string-per-user model so a user signed in on several
 * Android devices receives push notifications on all of them, and so a token
 * Firebase reports as invalid can be individually disabled without affecting
 * the user's other devices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void register(UUID userId, String token, String deviceId, String platform, String appVersion) {
        if (token == null || token.isBlank()) {
            return;
        }
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        deviceToken.setUserId(userId);
        deviceToken.setToken(token);
        deviceToken.setDeviceId(deviceId);
        deviceToken.setPlatform(platform != null && !platform.isBlank() ? platform : "ANDROID");
        deviceToken.setAppVersion(appVersion);
        deviceToken.setEnabled(true);
        deviceToken.setLastSeenAt(Instant.now());
        deviceTokenRepository.save(deviceToken);
        // Never log the raw token value.
        log.info("Registered device token for user {} (device={})", userId, deviceId);
    }

    @Transactional
    public void unregister(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void unregisterDevice(UUID userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<String> getActiveTokensForUser(UUID userId) {
        return deviceTokenRepository.findByUserIdAndEnabledTrue(userId)
                .stream()
                .map(DeviceToken::getToken)
                .toList();
    }

    /** Called when Firebase reports a token as unregistered/invalid. */
    @Transactional
    public void disableToken(String token) {
        deviceTokenRepository.findByToken(token).ifPresent(dt -> {
            dt.setEnabled(false);
            deviceTokenRepository.save(dt);
            log.info("Disabled invalid device token for user {}", dt.getUserId());
        });
    }
}
