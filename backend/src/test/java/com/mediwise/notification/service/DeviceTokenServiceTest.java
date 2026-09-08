package com.mediwise.notification.service;

import com.mediwise.notification.model.DeviceToken;
import com.mediwise.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceTokenService — Multi-device FCM Registry")
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("register: creates a new row for a token never seen before")
    void register_newToken() {
        when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.empty());

        deviceTokenService.register(userId, "token-abc", "device-1", "ANDROID", "1.0.0");

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        DeviceToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getToken()).isEqualTo("token-abc");
        assertThat(saved.getDeviceId()).isEqualTo("device-1");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("register: re-registering the same token updates the existing row instead of duplicating it")
    void register_existingToken_updatesInPlace() {
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("token-abc")
                .enabled(false)
                .build();
        when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

        deviceTokenService.register(userId, "token-abc", "device-1", "ANDROID", "1.0.1");

        assertThat(existing.getUserId()).isEqualTo(userId);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getAppVersion()).isEqualTo("1.0.1");
        verify(deviceTokenRepository).save(existing);
    }

    @Test
    @DisplayName("register: a blank token is ignored, never persisted")
    void register_blankToken_isNoOp() {
        deviceTokenService.register(userId, "  ", "device-1", "ANDROID", "1.0.0");

        verify(deviceTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("getActiveTokensForUser: only returns enabled tokens' raw values")
    void getActiveTokensForUser_returnsEnabledOnly() {
        when(deviceTokenRepository.findByUserIdAndEnabledTrue(userId)).thenReturn(List.of(
                DeviceToken.builder().token("token-1").enabled(true).build(),
                DeviceToken.builder().token("token-2").enabled(true).build()
        ));

        List<String> tokens = deviceTokenService.getActiveTokensForUser(userId);

        assertThat(tokens).containsExactlyInAnyOrder("token-1", "token-2");
    }

    @Test
    @DisplayName("disableToken: flips enabled to false without deleting the row")
    void disableToken_marksDisabled() {
        DeviceToken token = DeviceToken.builder().token("token-abc").userId(userId).enabled(true).build();
        when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(token));

        deviceTokenService.disableToken("token-abc");

        assertThat(token.isEnabled()).isFalse();
        verify(deviceTokenRepository).save(token);
    }

    @Test
    @DisplayName("unregister: deletes the row for that token")
    void unregister_deletesToken() {
        deviceTokenService.unregister("token-abc");

        verify(deviceTokenRepository).deleteByToken("token-abc");
    }
}
