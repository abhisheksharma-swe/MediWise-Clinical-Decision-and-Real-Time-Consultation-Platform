package com.mediwise.medicalrecord.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.medicalrecord.dto.*;
import com.mediwise.medicalrecord.model.PatientAllergy;
import com.mediwise.medicalrecord.model.PatientCondition;
import com.mediwise.medicalrecord.model.PatientMedication;
import com.mediwise.medicalrecord.repository.PatientAllergyRepository;
import com.mediwise.medicalrecord.repository.PatientConditionRepository;
import com.mediwise.medicalrecord.repository.PatientMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final PatientConditionRepository conditionRepository;
    private final PatientAllergyRepository allergyRepository;
    private final PatientMedicationRepository medicationRepository;
    private final AppointmentAuthorizationService authorizationService;

    /** Self-service: the calling patient's own record. */
    public MedicalRecordResponse getMyRecord(User user) {
        return getRecord(resolveOwnPatientId(user), user);
    }

    public UUID resolveOwnPatientId(User user) {
        UUID patientId = authorizationService.resolvePatientId(user);
        if (patientId == null) {
            throw new ResourceNotFoundException("Patient profile", user.getId().toString());
        }
        return patientId;
    }

    public MedicalRecordResponse getRecord(UUID patientId, User user) {
        authorizationService.assertCanViewPatientHistory(patientId, user);
        return MedicalRecordResponse.builder()
                .patientId(patientId)
                .conditions(conditionRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                        .map(ConditionResponse::from).toList())
                .allergies(allergyRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                        .map(AllergyResponse::from).toList())
                .medications(medicationRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                        .map(MedicationResponse::from).toList())
                .build();
    }

    @Transactional
    public ConditionResponse addCondition(UUID patientId, User user, CreateConditionRequest request) {
        boolean selfReported = authorizeWrite(patientId, user);
        PatientCondition condition = PatientCondition.builder()
                .patientId(patientId)
                .name(request.getName())
                .diagnosedDate(request.getDiagnosedDate())
                .notes(request.getNotes())
                .sourceConsultationId(selfReported ? null : request.getSourceConsultationId())
                .selfReported(selfReported)
                .build();
        return ConditionResponse.from(conditionRepository.save(condition));
    }

    @Transactional
    public AllergyResponse addAllergy(UUID patientId, User user, CreateAllergyRequest request) {
        boolean selfReported = authorizeWrite(patientId, user);
        PatientAllergy allergy = PatientAllergy.builder()
                .patientId(patientId)
                .allergen(request.getAllergen())
                .reaction(request.getReaction())
                .severity(request.getSeverity())
                .notes(request.getNotes())
                .selfReported(selfReported)
                .build();
        return AllergyResponse.from(allergyRepository.save(allergy));
    }

    @Transactional
    public MedicationResponse addMedication(UUID patientId, User user, CreateMedicationRequest request) {
        boolean selfReported = authorizeWrite(patientId, user);
        PatientMedication medication = PatientMedication.builder()
                .patientId(patientId)
                .name(request.getName())
                .dosage(request.getDosage())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(true)
                .sourcePrescriptionItemId(selfReported ? null : request.getSourcePrescriptionItemId())
                .selfReported(selfReported)
                .build();
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    @Transactional
    public ConditionResponse updateConditionStatus(UUID conditionId, User user, UpdateConditionStatusRequest request) {
        PatientCondition condition = conditionRepository.findById(conditionId)
                .orElseThrow(() -> new ResourceNotFoundException("Condition", conditionId.toString()));
        authorizationService.assertCanViewPatientHistory(condition.getPatientId(), user);
        condition.setStatus(request.getStatus());
        return ConditionResponse.from(conditionRepository.save(condition));
    }

    /** Returns true if this is a self-reported (patient-authored) entry. */
    private boolean authorizeWrite(UUID patientId, User user) {
        if (user.getRole() == User.Role.PATIENT) {
            UUID ownPatientId = authorizationService.resolvePatientId(user);
            if (!patientId.equals(ownPatientId)) {
                throw new BusinessException("FORBIDDEN", "You can only add entries to your own medical record.");
            }
            return true;
        }
        if (user.getRole() == User.Role.DOCTOR) {
            authorizationService.assertCanViewPatientHistory(patientId, user);
            return false;
        }
        throw new BusinessException("FORBIDDEN", "Only patients or treating doctors can add medical record entries.");
    }
}
