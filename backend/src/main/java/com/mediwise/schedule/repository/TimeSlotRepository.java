package com.mediwise.schedule.repository;

import com.mediwise.schedule.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    @Query("SELECT s FROM TimeSlot s WHERE s.doctorId = :doctorId " +
            "AND s.slotDate = :date AND s.status = 'AVAILABLE' " +
            "ORDER BY s.startTime ASC")
    List<TimeSlot> findAvailableSlots(@Param("doctorId") UUID doctorId,
                                      @Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE TimeSlot s SET s.status = 'AVAILABLE', s.lockedBy = null, " +
            "s.lockedUntil = null WHERE s.lockedUntil < CURRENT_TIMESTAMP " +
            "AND s.status = 'LOCKED'")
    void releaseExpiredLocks();

    @Modifying
    @Query("UPDATE TimeSlot s SET s.status = 'LOCKED', s.lockedBy = :userId, " +
            "s.lockedUntil = :expiresAt WHERE s.id = :slotId AND s.status = 'AVAILABLE'")
    int tryLockSlot(@Param("slotId") UUID slotId,
                    @Param("userId") UUID userId,
                    @Param("expiresAt") Instant expiresAt);

    /**
     * Atomically consumes a slot this user holds the lock on, turning it into
     * a permanent booking. Conditioned on the current LOCKED/lockedBy state so
     * two near-simultaneous booking attempts for the same slot can't both
     * succeed — the loser gets 0 rows updated instead of silently overwriting
     * the winner's row (a plain read-modify-save here would race).
     */
    @Modifying
    @Query("UPDATE TimeSlot s SET s.status = 'BOOKED', s.lockedBy = null, " +
            "s.lockedUntil = null WHERE s.id = :slotId AND s.status = 'LOCKED' AND s.lockedBy = :userId")
    int tryConsumeLockedSlot(@Param("slotId") UUID slotId, @Param("userId") UUID userId);
}