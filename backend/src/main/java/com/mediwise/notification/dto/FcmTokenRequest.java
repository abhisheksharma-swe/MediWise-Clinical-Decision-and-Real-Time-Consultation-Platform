package com.mediwise.notification.dto;

import lombok.Data;

@Data
public class FcmTokenRequest {
    private String fcmToken;
    private String deviceId;
    private String platform;
    private String appVersion;
}
