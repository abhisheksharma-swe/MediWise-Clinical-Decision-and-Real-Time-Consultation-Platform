package com.mediwise.prescription.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import com.mediwise.prescription.dto.CreatePrescriptionRequest;
import com.mediwise.prescription.dto.PrescriptionItemRequest;
import com.mediwise.prescription.dto.PrescriptionResponse;
import com.mediwise.prescription.model.Prescription;
import com.mediwise.prescription.model.PrescriptionItem;
import com.mediwise.prescription.repository.PrescriptionItemRepository;
import com.mediwise.prescription.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionService")
class PrescriptionServiceTest {

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PrescriptionItemRepository prescriptionItemRepository;
    @Mock private ConsultationRepository consultationRepository;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private PrescriptionService prescriptionService;

    private UUID consultationId;
    private UUID patientId;
    private UUID doctorId;
    private User doctorUser;
    private User patientUser;
    private Consultation consultation;

    @BeforeEach
    void setUp() {
        consultationId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        doctorUser = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
        patientUser = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        consultation = Consultation.builder().id(consultationId).patientId(patientId).doctorId(doctorId).build();
    }

    private CreatePrescriptionRequest requestWithOneItem() {
        PrescriptionItemRequest item = new PrescriptionItemRequest();
        item.setMedicineName("Paracetamol");
        item.setDosage("500mg");
        CreatePrescriptionRequest request = new CreatePrescriptionRequest();
        request.setItems(List.of(item));
        return request;
    }

    @Test
    @DisplayName("create: the owning doctor can create a prescription with its items in order")
    void create_success() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(authorizationService.requireDoctorId(doctorUser)).thenReturn(doctorId);
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(prescriptionItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        PrescriptionResponse response = prescriptionService.create(consultationId, doctorUser, requestWithOneItem());

        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getMedicineName()).isEqualTo("Paracetamol");
    }

    @Test
    @DisplayName("create: forbidden for a doctor who did not conduct this consultation")
    void create_wrongDoctor_forbidden() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(authorizationService.requireDoctorId(doctorUser)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> prescriptionService.create(consultationId, doctorUser, requestWithOneItem()))
                .isInstanceOf(BusinessException.class);
        verify(prescriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: an unknown consultation throws ResourceNotFoundException")
    void create_missingConsultation_throws() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.create(consultationId, doctorUser, requestWithOneItem()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getById: an authorized viewer gets the prescription with its items")
    void getById_success() {
        UUID id = UUID.randomUUID();
        Prescription prescription = Prescription.builder().id(id).patientId(patientId).doctorId(doctorId).build();
        when(prescriptionRepository.findById(id)).thenReturn(Optional.of(prescription));
        when(prescriptionItemRepository.findByPrescriptionIdOrderBySortOrderAsc(id))
                .thenReturn(List.of(PrescriptionItem.builder().id(UUID.randomUUID()).medicineName("Ibuprofen").build()));

        PrescriptionResponse response = prescriptionService.getById(id, patientUser);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
        assertThat(response.getItems()).extracting("medicineName").containsExactly("Ibuprofen");
    }

    @Test
    @DisplayName("getById: an unrelated viewer is forbidden")
    void getById_forbidden() {
        UUID id = UUID.randomUUID();
        Prescription prescription = Prescription.builder().id(id).patientId(patientId).doctorId(doctorId).build();
        when(prescriptionRepository.findById(id)).thenReturn(Optional.of(prescription));
        doThrow(new BusinessException("FORBIDDEN", "no access"))
                .when(authorizationService).assertCanViewPatientHistory(patientId, patientUser);

        assertThatThrownBy(() -> prescriptionService.getById(id, patientUser))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getForPatient: returns a page of prescriptions with their items attached")
    void getForPatient_returnsPageWithItems() {
        Prescription prescription = Prescription.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(prescription)));
        when(prescriptionItemRepository.findByPrescriptionIdInOrderBySortOrderAsc(List.of(prescription.getId())))
                .thenReturn(List.of());

        Page<PrescriptionResponse> page = prescriptionService.getForPatient(patientId, doctorUser, 0, 20);

        verify(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMyPrescriptions: resolves the caller's own patient id first")
    void getMyPrescriptions_resolvesOwnPatientId() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        prescriptionService.getMyPrescriptions(patientUser, 0, 20);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
    }

    @Test
    @DisplayName("getMyPrescriptions: a user with no patient profile gets a clear not-found error")
    void getMyPrescriptions_noProfile_throws() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(null);

        assertThatThrownBy(() -> prescriptionService.getMyPrescriptions(patientUser, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
