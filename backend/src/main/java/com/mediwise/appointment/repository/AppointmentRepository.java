package com.mediwise.appointment.repository;

import com.mediwise.appointment.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Appointment> {

    Page<Appointment> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    Page<Appointment> findByPatientIdAndStatusOrderByCreatedAtDesc(
            UUID patientId, Appointment.AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByPatientIdAndStatusInOrderByCreatedAtDesc(
            UUID patientId, java.util.Collection<Appointment.AppointmentStatus> statuses, Pageable pageable);

    Page<Appointment> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId, Pageable pageable);

    Page<Appointment> findByDoctorIdAndStatusInOrderByCreatedAtDesc(
            UUID doctorId, java.util.Collection<Appointment.AppointmentStatus> statuses, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctorId = :doctorId " +
            "AND a.status IN ('PENDING','CONFIRMED','IN_PROGRESS')")
    long countActiveByDoctorId(@Param("doctorId") UUID doctorId);

    long countByStatus(Appointment.AppointmentStatus status);

    boolean existsByDoctorIdAndPatientId(UUID doctorId, UUID patientId);

    /**
     * A slot can be booked, cancelled, and rebooked by someone else over its
     * lifetime, so several rows may share the same slot_id historically — this
     * only ever returns the current non-cancelled owner (at most one can exist
     * at a time), used to make appointment creation idempotent on retry.
     */
    java.util.Optional<Appointment> findFirstBySlotIdAndStatusNot(
            UUID slotId, Appointment.AppointmentStatus excludedStatus);

    /** PENDING appointments whose payment window has elapsed without confirmation. */
    java.util.List<Appointment> findByStatusAndCreatedAtBefore(
            Appointment.AppointmentStatus status, Instant cutoff);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE (:doctorId IS NULL OR a.doctorId = :doctorId) " +
            "AND a.createdAt BETWEEN :start AND :end")
    long countByDoctorIdAndCreatedAtBetween(
            @Param("doctorId") UUID doctorId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE (:doctorId IS NULL OR a.doctorId = :doctorId) " +
            "AND a.status = :status AND a.createdAt BETWEEN :start AND :end")
    long countByDoctorIdAndStatusAndCreatedAtBetween(
            @Param("doctorId") UUID doctorId,
            @Param("status") Appointment.AppointmentStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);
}