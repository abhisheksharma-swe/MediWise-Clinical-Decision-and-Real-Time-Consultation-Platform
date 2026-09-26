package com.mediwise.consultation.repository;

import com.mediwise.consultation.model.Consultation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findByAppointmentId(UUID appointmentId);

    Page<Consultation> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
