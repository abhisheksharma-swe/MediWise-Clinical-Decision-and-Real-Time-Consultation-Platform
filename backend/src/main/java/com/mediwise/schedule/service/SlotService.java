package com.mediwise.schedule.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.exception.SlotConflictException;
import com.mediwise.schedule.dto.SlotLockResponse;
import com.mediwise.schedule.dto.SlotResponse;
import com.mediwise.schedule.model.TimeSlot;
import com.mediwise.schedule.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private final TimeSlotRepository slotRepository;
    
    @Autowired(required = false)
    private RedissonClient redissonClient;

    private static final long LOCK_TTL_MINUTES = 5;

    @Transactional
    public List<SlotResponse> getAvailableSlots(UUID doctorId, LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }

        List<TimeSlot> existing = slotRepository.findAvailableSlots(doctorId, date);
        if (!existing.isEmpty()) {
            return existing.stream().map(SlotResponse::from).toList();
        }

        // Auto-generate standard 30-min clinic slots for future dates (up to 30 days)
        if (!date.isAfter(LocalDate.now().plusDays(30))) {
            List<java.time.LocalTime> startTimes = List.of(
                    java.time.LocalTime.of(9, 0),
                    java.time.LocalTime.of(9, 30),
                    java.time.LocalTime.of(10, 0),
                    java.time.LocalTime.of(10, 30),
                    java.time.LocalTime.of(11, 0),
                    java.time.LocalTime.of(11, 30),
                    java.time.LocalTime.of(14, 0),
                    java.time.LocalTime.of(14, 30),
                    java.time.LocalTime.of(15, 0),
                    java.time.LocalTime.of(15, 30),
                    java.time.LocalTime.of(16, 0),
                    java.time.LocalTime.of(16, 30)
            );

            List<TimeSlot> newSlots = startTimes.stream().map(start -> TimeSlot.builder()
                    .doctorId(doctorId)
                    .slotDate(date)
                    .startTime(start)
                    .endTime(start.plusMinutes(30))
                    .status(TimeSlot.SlotStatus.AVAILABLE)
                    .build()
            ).toList();

            try {
                slotRepository.saveAll(newSlots);
                return newSlots.stream().map(SlotResponse::from).toList();
            } catch (Exception e) {
                log.warn("Slot concurrent creation notice for doctor {} on date {}", doctorId, date);
                return slotRepository.findAvailableSlots(doctorId, date).stream().map(SlotResponse::from).toList();
            }
        }

        return List.of();
    }

    @Transactional
    public SlotLockResponse lockSlot(UUID slotId, User user) {
        TimeSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId.toString()));

        if (redissonClient == null) {
            Instant expiresAt = Instant.now().plusSeconds(LOCK_TTL_MINUTES * 60);
            int updated = slotRepository.tryLockSlot(slotId, user.getId(), expiresAt);
            if (updated == 0) {
                throw new SlotConflictException();
            }

            log.info("Slot {} locked by user {} (without distributed lock)", slotId, user.getId());
            return SlotLockResponse.builder()
                    .slotId(slotId)
                    .locked(true)
                    .expiresAt(expiresAt)
                    .ttlMinutes(LOCK_TTL_MINUTES)
                    .build();
        }

        // Use Redisson distributed lock to prevent race conditions
        RLock rLock = redissonClient.getLock("slot_lock:" + slotId);
        try {
            boolean acquired = rLock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new SlotConflictException();
            }

            // Re-fetch inside lock
            slot = slotRepository.findById(slotId).orElseThrow();
            if (slot.getStatus() != TimeSlot.SlotStatus.AVAILABLE) {
                throw new SlotConflictException();
            }

            Instant expiresAt = Instant.now().plusSeconds(LOCK_TTL_MINUTES * 60);
            slot.setStatus(TimeSlot.SlotStatus.LOCKED);
            slot.setLockedBy(user.getId());
            slot.setLockedUntil(expiresAt);
            slotRepository.save(slot);

            log.info("Slot {} locked by user {}", slotId, user.getId());
            return SlotLockResponse.builder()
                    .slotId(slotId)
                    .locked(true)
                    .expiresAt(expiresAt)
                    .ttlMinutes(LOCK_TTL_MINUTES)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("LOCK_FAILED", "Could not acquire slot lock. Try again.");
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    @Transactional
    public void releaseSlot(UUID slotId, User user) {
        TimeSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId.toString()));

        if (!user.getId().equals(slot.getLockedBy())) {
            throw new BusinessException("LOCK_OWNER_MISMATCH", "You do not hold the lock for this slot.");
        }

        slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
        slot.setLockedBy(null);
        slot.setLockedUntil(null);
        slotRepository.save(slot);
    }

    @Scheduled(fixedDelay = 60_000) // every 1 minute
    @Transactional
    public void cleanExpiredLocks() {
        slotRepository.releaseExpiredLocks();
    }
}
