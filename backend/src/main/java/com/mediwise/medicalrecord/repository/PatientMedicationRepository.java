package com.mediwise.medicalrecord.repository;

import com.mediwise.medicalrecord.model.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientMedicationRepository extends JpaRepository<PatientMedication, UUID> {

    List<PatientMedication> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
