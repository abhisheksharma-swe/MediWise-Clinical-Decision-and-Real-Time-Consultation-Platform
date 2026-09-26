package com.mediwise.medicalrecord.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.medicalrecord.dto.*;
import com.mediwise.medicalrecord.model.PatientAllergy;
import com.mediwise.medicalrecord.model.PatientCondition;
import com.mediwise.medicalrecord.repository.PatientAllergyRepository;
import com.mediwise.medicalrecord.repository.PatientConditionRepository;
import com.mediwise.medicalrecord.repository.PatientMedicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicalRecordService")
class MedicalRecordServiceTest {

    @Mock private PatientConditionRepository conditionRepository;
    @Mock private PatientAllergyRepository allergyRepository;
    @Mock private PatientMedicationRepository medicationRepository;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private MedicalRecordService medicalRecordService;

    private UUID patientId;
    private User patientUser;
    private User doctorUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        patientUser = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        doctorUser = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
        adminUser = User.builder().id(UUID.randomUUID()).role(User.Role.ADMIN).build();
    }

    private CreateConditionRequest conditionRequest() {
        CreateConditionRequest request = new CreateConditionRequest();
        request.setName("Hypertension");
        request.setSourceConsultationId(UUID.randomUUID());
        return request;
    }

    @Test
    @DisplayName("addCondition: a patient adding to their own record is recorded as self-reported, ignoring any sourceConsultationId")
    void addCondition_ownPatient_selfReported() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(conditionRepository.save(any(PatientCondition.class))).thenAnswer(inv -> inv.getArgument(0));

        ConditionResponse response = medicalRecordService.addCondition(patientId, patientUser, conditionRequest());

        ArgumentCaptor<PatientCondition> captor = ArgumentCaptor.forClass(PatientCondition.class);
        verify(conditionRepository).save(captor.capture());
        assertThat(captor.getValue().isSelfReported()).isTrue();
        assertThat(captor.getValue().getSourceConsultationId()).isNull();
        assertThat(response.isSelfReported()).isTrue();
    }

    @Test
    @DisplayName("addCondition: a patient cannot add entries to another patient's record")
    void addCondition_otherPatient_forbidden() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> medicalRecordService.addCondition(patientId, patientUser, conditionRequest()))
                .isInstanceOf(BusinessException.class);
        verify(conditionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addCondition: a treating doctor's entry is doctor-authored and keeps the source consultation")
    void addCondition_treatingDoctor_doctorAuthored() {
        when(conditionRepository.save(any(PatientCondition.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateConditionRequest request = conditionRequest();

        ConditionResponse response = medicalRecordService.addCondition(patientId, doctorUser, request);

        verify(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);
        ArgumentCaptor<PatientCondition> captor = ArgumentCaptor.forClass(PatientCondition.class);
        verify(conditionRepository).save(captor.capture());
        assertThat(captor.getValue().isSelfReported()).isFalse();
        assertThat(captor.getValue().getSourceConsultationId()).isEqualTo(request.getSourceConsultationId());
        assertThat(response.isSelfReported()).isFalse();
    }

    @Test
    @DisplayName("addCondition: a doctor with no relationship to the patient is forbidden")
    void addCondition_unrelatedDoctor_forbidden() {
        doThrow(new BusinessException("FORBIDDEN", "no relationship"))
                .when(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);

        assertThatThrownBy(() -> medicalRecordService.addCondition(patientId, doctorUser, conditionRequest()))
                .isInstanceOf(BusinessException.class);
        verify(conditionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addCondition: an admin cannot add medical record entries")
    void addCondition_admin_forbidden() {
        assertThatThrownBy(() -> medicalRecordService.addCondition(patientId, adminUser, conditionRequest()))
                .isInstanceOf(BusinessException.class);
        verify(conditionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addAllergy: a self-reported entry from the owning patient is saved as such")
    void addAllergy_ownPatient_selfReported() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(allergyRepository.save(any(PatientAllergy.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateAllergyRequest request = new CreateAllergyRequest();
        request.setAllergen("Penicillin");

        AllergyResponse response = medicalRecordService.addAllergy(patientId, patientUser, request);

        assertThat(response.isSelfReported()).isTrue();
        assertThat(response.getAllergen()).isEqualTo("Penicillin");
    }

    @Test
    @DisplayName("updateConditionStatus: an authorized user can mark a condition resolved")
    void updateConditionStatus_success() {
        UUID conditionId = UUID.randomUUID();
        PatientCondition condition = PatientCondition.builder().id(conditionId).patientId(patientId)
                .status(PatientCondition.Status.ACTIVE).build();
        when(conditionRepository.findById(conditionId)).thenReturn(Optional.of(condition));
        when(conditionRepository.save(any(PatientCondition.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateConditionStatusRequest request = new UpdateConditionStatusRequest();
        request.setStatus(PatientCondition.Status.RESOLVED);

        ConditionResponse response = medicalRecordService.updateConditionStatus(conditionId, patientUser, request);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
        assertThat(response.getStatus()).isEqualTo(PatientCondition.Status.RESOLVED);
    }

    @Test
    @DisplayName("updateConditionStatus: an unknown condition throws ResourceNotFoundException")
    void updateConditionStatus_missing_throws() {
        UUID conditionId = UUID.randomUUID();
        when(conditionRepository.findById(conditionId)).thenReturn(Optional.empty());

        UpdateConditionStatusRequest request = new UpdateConditionStatusRequest();
        request.setStatus(PatientCondition.Status.RESOLVED);

        assertThatThrownBy(() -> medicalRecordService.updateConditionStatus(conditionId, patientUser, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getRecord: aggregates conditions, allergies and medications for an authorized viewer")
    void getRecord_aggregatesAllThree() {
        when(conditionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(java.util.List.of());
        when(allergyRepository.findByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(java.util.List.of());
        when(medicationRepository.findByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(java.util.List.of());

        MedicalRecordResponse response = medicalRecordService.getRecord(patientId, patientUser);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getConditions()).isEmpty();
        assertThat(response.getAllergies()).isEmpty();
        assertThat(response.getMedications()).isEmpty();
    }
}
