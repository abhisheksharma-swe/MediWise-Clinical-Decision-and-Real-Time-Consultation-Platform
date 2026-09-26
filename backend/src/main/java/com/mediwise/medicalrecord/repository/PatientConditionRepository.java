package com.mediwise.medicalrecord.repository;

import com.mediwise.medicalrecord.model.PatientCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientConditionRepository extends JpaRepository<PatientCondition, UUID> {

    List<PatientCondition> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
