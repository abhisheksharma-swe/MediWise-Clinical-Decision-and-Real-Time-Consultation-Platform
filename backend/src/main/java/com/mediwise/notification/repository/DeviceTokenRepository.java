package com.mediwise.notification.repository;

import com.mediwise.notification.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdAndEnabledTrue(UUID userId);

    void deleteByToken(String token);

    void deleteByUserIdAndDeviceId(UUID userId, String deviceId);
}
