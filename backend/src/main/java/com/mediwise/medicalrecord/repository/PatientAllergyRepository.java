package com.mediwise.medicalrecord.repository;

import com.mediwise.medicalrecord.model.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, UUID> {

    List<PatientAllergy> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
