package com.mediwise.notification.controller;

import com.mediwise.auth.model.User;
import com.mediwise.common.response.ApiResponse;
import com.mediwise.common.response.PagedResponse;
import com.mediwise.notification.dto.FcmTokenRequest;
import com.mediwise.notification.model.Notification;
import com.mediwise.notification.repository.NotificationRepository;
import com.mediwise.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification feed and FCM token registration")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenService deviceTokenService;

    @GetMapping
    @Operation(summary = "Get notification feed")
    public ResponseEntity<ApiResponse<PagedResponse<Notification>>> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(notificationRepository.findByUserIdOrderBySentAtDesc(user.getId(), pageable))));
    }

    @PatchMapping("/{id}/read")
    @Transactional
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal User user) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(user.getId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
        return ResponseEntity.ok(ApiResponse.message("Marked as read"));
    }

    @PatchMapping("/read-all")
    @Transactional
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal User user) {
        notificationRepository.markAllReadByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.message("All notifications marked as read"));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "Register FCM device token for this device (multi-device safe)")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal User user,
            @RequestBody FcmTokenRequest request) {
        deviceTokenService.register(
                user.getId(), request.getFcmToken(), request.getDeviceId(),
                request.getPlatform(), request.getAppVersion());
        return ResponseEntity.ok(ApiResponse.message("FCM token registered"));
    }

    @DeleteMapping("/fcm-token")
    @Operation(summary = "Unregister this device's FCM token (call on logout)")
    public ResponseEntity<ApiResponse<Void>> unregisterToken(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) FcmTokenRequest request) {
        if (request != null && request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
            deviceTokenService.unregister(request.getFcmToken());
        } else if (request != null && request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            deviceTokenService.unregisterDevice(user.getId(), request.getDeviceId());
        }
        return ResponseEntity.ok(ApiResponse.message("FCM token unregistered"));
    }
}
