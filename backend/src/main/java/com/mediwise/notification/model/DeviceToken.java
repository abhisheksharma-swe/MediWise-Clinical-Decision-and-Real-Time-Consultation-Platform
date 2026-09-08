package com.mediwise.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A single Android/iOS/web device's FCM registration for a user. One user can
 * have many enabled tokens at once (multiple devices); a device re-registering
 * (e.g. on app reinstall) reuses its row via the unique token constraint.
 */
@Entity
@Table(name = "device_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 4096, unique = true)
    private String token;

    @Column(name = "device_id")
    private String deviceId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String platform = "ANDROID";

    @Column(name = "app_version")
    private String appVersion;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_seen_at")
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
