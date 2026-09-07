package com.mediwise.appointment.repository;

import com.mediwise.appointment.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    long countByDoctorIdAndCreatedAtBetween(UUID doctorId, java.time.Instant start, java.time.Instant end);

    long countByDoctorIdAndStatusAndCreatedAtBetween(UUID doctorId, Appointment.AppointmentStatus status, java.time.Instant start, java.time.Instant end);
    boolean existsByDoctorIdAndPatientId(UUID doctorId, UUID patientId);
}
