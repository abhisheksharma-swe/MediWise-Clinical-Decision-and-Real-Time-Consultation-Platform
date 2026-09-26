package com.mediwise.medicalrecord.repository;

import com.mediwise.medicalrecord.model.MedicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {

    List<MedicalDocument> findByPatientIdOrderByUploadedAtDesc(UUID patientId);
}
