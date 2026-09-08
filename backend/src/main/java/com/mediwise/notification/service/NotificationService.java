package com.mediwise.notification.service;

import com.mediwise.notification.model.Notification;
import com.mediwise.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FirebasePushService firebasePushService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Persists a notification to PostgreSQL, then dispatches it over both
     * channels: STOMP for an immediate in-app update, and FCM for background/
     * system-tray delivery. Both reference the same persisted notification ID
     * so Android can deduplicate regardless of which one arrives first.
     */
    @Transactional
    public Notification send(UUID userId, String title, String body, String type, UUID refId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setType(type);
        notification.setRefId(refId);
        Notification saved = notificationRepository.save(notification);

        // Fire-and-forget realtime + push (failures logged, never thrown —
        // the persisted notification is the source of truth regardless).
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    Map.of(
                            "id", saved.getId().toString(),
                            "type", saved.getType(),
                            "title", saved.getTitle(),
                            "body", saved.getBody() != null ? saved.getBody() : "",
                            "refId", saved.getRefId() != null ? saved.getRefId().toString() : "",
                            "read", saved.isRead(),
                            "sentAt", saved.getSentAt().toString()
                    ));
        } catch (Exception ex) {
            log.warn("STOMP notification delivery failed for user {}: {}", userId, ex.getMessage());
        }

        try {
            firebasePushService.sendToUser(userId, title, body,
                    Map.of("notificationId", saved.getId().toString(), "type", type, "refId",
                            refId != null ? refId.toString() : ""));
        } catch (Exception ex) {
            log.warn("FCM push failed for user {}: {}", userId, ex.getMessage());
        }

        return saved;
    }

    /**
     * Get paginated notifications for a user, newest first.
     */
    @Transactional(readOnly = true)
    public Page<Notification> getForUser(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderBySentAtDesc(userId, pageable);
    }

    /**
     * Mark a single notification as read.
     */
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    /**
     * Mark all notifications for a user as read.
     */
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * Count unread notifications (used for badge counts).
     */
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
}
