package com.mediwise.followup.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import com.mediwise.followup.dto.CreateFollowUpRequest;
import com.mediwise.followup.dto.FollowUpResponse;
import com.mediwise.followup.model.FollowUp;
import com.mediwise.followup.repository.FollowUpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowUpService")
class FollowUpServiceTest {

    @Mock private FollowUpRepository followUpRepository;
    @Mock private ConsultationRepository consultationRepository;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private FollowUpService followUpService;

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

    @Test
    @DisplayName("create: the owning doctor can recommend a follow-up, starting PENDING")
    void create_success() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(authorizationService.requireDoctorId(doctorUser)).thenReturn(doctorId);
        when(followUpRepository.save(any(FollowUp.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateFollowUpRequest request = new CreateFollowUpRequest();
        request.setReason("Re-check blood pressure");

        FollowUpResponse response = followUpService.create(consultationId, doctorUser, request);

        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getStatus()).isEqualTo(FollowUp.Status.PENDING);
        assertThat(response.getReason()).isEqualTo("Re-check blood pressure");
    }

    @Test
    @DisplayName("create: forbidden for a doctor who did not conduct this consultation")
    void create_wrongDoctor_forbidden() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(authorizationService.requireDoctorId(doctorUser)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> followUpService.create(consultationId, doctorUser, new CreateFollowUpRequest()))
                .isInstanceOf(BusinessException.class);
        verify(followUpRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: an unknown consultation throws ResourceNotFoundException")
    void create_missingConsultation_throws() {
        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followUpService.create(consultationId, doctorUser, new CreateFollowUpRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getForPatient: delegates to the authorization service")
    void getForPatient_authorized() {
        when(followUpRepository.findByPatientIdOrderByRecommendedDateAsc(patientId)).thenReturn(List.of());

        followUpService.getForPatient(patientId, doctorUser);

        verify(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);
    }

    @Test
    @DisplayName("getMyFollowUps: resolves the caller's own patient id")
    void getMyFollowUps_resolvesOwnPatientId() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(followUpRepository.findByPatientIdOrderByRecommendedDateAsc(patientId)).thenReturn(List.of());

        followUpService.getMyFollowUps(patientUser);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
    }

    @Test
    @DisplayName("getMyFollowUps: a user with no patient profile gets a clear not-found error")
    void getMyFollowUps_noProfile_throws() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(null);

        assertThatThrownBy(() -> followUpService.getMyFollowUps(patientUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("dismiss: the owning patient can dismiss their own follow-up")
    void dismiss_ownFollowUp_success() {
        UUID followUpId = UUID.randomUUID();
        FollowUp followUp = FollowUp.builder().id(followUpId).patientId(patientId).doctorId(doctorId)
                .status(FollowUp.Status.PENDING).build();
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(followUpRepository.save(any(FollowUp.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUpResponse response = followUpService.dismiss(followUpId, patientUser);

        assertThat(response.getStatus()).isEqualTo(FollowUp.Status.DISMISSED);
    }

    @Test
    @DisplayName("dismiss: a stranger patient cannot dismiss someone else's follow-up")
    void dismiss_otherPatient_forbidden() {
        UUID followUpId = UUID.randomUUID();
        FollowUp followUp = FollowUp.builder().id(followUpId).patientId(patientId).doctorId(doctorId)
                .status(FollowUp.Status.PENDING).build();
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> followUpService.dismiss(followUpId, patientUser))
                .isInstanceOf(BusinessException.class);
        verify(followUpRepository, never()).save(any());
    }

    @Test
    @DisplayName("dismiss: an unknown follow-up throws ResourceNotFoundException")
    void dismiss_missing_throws() {
        UUID followUpId = UUID.randomUUID();
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followUpService.dismiss(followUpId, patientUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
