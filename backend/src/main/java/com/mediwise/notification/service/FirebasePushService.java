package com.mediwise.notification.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebasePushService {

    private final DeviceTokenService deviceTokenService;

    /** Pushes to every active device registered for this user. */
    public void sendToUser(UUID userId, String title, String body) {
        sendToUser(userId, title, body, Map.of());
    }

    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenService.getActiveTokensForUser(userId);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens for user {} — skipping push", userId);
            return;
        }
        sendToMultiple(tokens, title, body, data);
    }

    public void sendToToken(String fcmToken, String title, String body, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent: {}", response);
        } catch (FirebaseMessagingException e) {
            handleSendFailure(fcmToken, e);
        }
    }

    public void sendToMultiple(List<String> tokens, String title, String body) {
        sendToMultiple(tokens, title, body, Map.of());
    }

    public void sendToMultiple(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens.isEmpty()) return;
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM multicast: {} success / {} failure",
                    response.getSuccessCount(), response.getFailureCount());

            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse r = responses.get(i);
                    if (!r.isSuccessful() && r.getException() != null) {
                        handleSendFailure(tokens.get(i), r.getException());
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM multicast failed: {}", e.getMessage());
        }
    }

    /**
     * Disables a token that Firebase reports as permanently invalid, so it
     * stops being retried on every future push to this user's other devices.
     */
    private void handleSendFailure(String token, FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        log.warn("FCM send failed ({}): {}", code, e.getMessage());
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
            deviceTokenService.disableToken(token);
        }
    }
}
