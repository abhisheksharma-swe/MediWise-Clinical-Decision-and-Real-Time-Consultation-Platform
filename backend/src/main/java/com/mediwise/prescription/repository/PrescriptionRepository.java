package com.mediwise.prescription.repository;

import com.mediwise.prescription.model.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByConsultationId(UUID consultationId);

    Page<Prescription> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
