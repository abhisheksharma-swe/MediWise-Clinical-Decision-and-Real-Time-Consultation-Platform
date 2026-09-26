package com.mediwise.consultation.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.consultation.dto.ConsultationResponse;
import com.mediwise.consultation.dto.UpdateConsultationRequest;
import com.mediwise.consultation.model.Consultation;
import com.mediwise.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("ConsultationService")
class ConsultationServiceTest {

    @Mock private ConsultationRepository consultationRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private ConsultationService consultationService;

    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private User doctorUser;
    private User patientUser;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        appointmentId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        doctorUser = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
        patientUser = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        appointment = Appointment.builder()
                .id(appointmentId).patientId(patientId).doctorId(doctorId)
                .status(Appointment.AppointmentStatus.IN_PROGRESS)
                .build();
    }

    @Test
    @DisplayName("getForAppointment: returns null (not an error) when no consultation has been started yet")
    void getForAppointment_noneYet_returnsNull() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());

        ConsultationResponse response = consultationService.getForAppointment(appointmentId, patientUser);

        assertThat(response).isNull();
        verify(authorizationService).assertCanAccessAppointment(appointment, patientUser);
    }

    @Test
    @DisplayName("getForAppointment: returns the existing consultation when present")
    void getForAppointment_existing_returnsResponse() {
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID()).appointmentId(appointmentId).patientId(patientId).doctorId(doctorId)
                .build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(consultation));

        ConsultationResponse response = consultationService.getForAppointment(appointmentId, patientUser);

        assertThat(response).isNotNull();
        assertThat(response.getAppointmentId()).isEqualTo(appointmentId);
    }

    @Test
    @DisplayName("getForAppointment: an unknown appointment throws ResourceNotFoundException")
    void getForAppointment_missingAppointment_throws() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.getForAppointment(appointmentId, patientUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getForAppointment: propagates the authorization service's denial")
    void getForAppointment_forbidden_propagates() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        doThrow(new BusinessException("FORBIDDEN", "no access"))
                .when(authorizationService).assertCanAccessAppointment(appointment, patientUser);

        assertThatThrownBy(() -> consultationService.getForAppointment(appointmentId, patientUser))
                .isInstanceOf(BusinessException.class);
        verify(consultationRepository, never()).findByAppointmentId(any());
    }

    @Test
    @DisplayName("upsert: creates a new consultation carrying over the appointment's patient/doctor ids")
    void upsert_createsNew() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateConsultationRequest request = new UpdateConsultationRequest();
        request.setChiefComplaint("Headache");
        request.setObservations("Mild fever");

        ConsultationResponse response = consultationService.upsert(appointmentId, doctorUser, request);

        verify(authorizationService).assertDoctorOwnsAppointment(appointment, doctorUser);
        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(patientId);
        assertThat(captor.getValue().getDoctorId()).isEqualTo(doctorId);
        assertThat(captor.getValue().getChiefComplaint()).isEqualTo("Headache");
        assertThat(captor.getValue().getObservations()).isEqualTo("Mild fever");
        assertThat(captor.getValue().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getChiefComplaint()).isEqualTo("Headache");
    }

    @Test
    @DisplayName("upsert: only overwrites fields present on the request, leaving the rest untouched")
    void upsert_partialUpdate_doesNotClearOtherFields() {
        Consultation existing = Consultation.builder()
                .id(UUID.randomUUID()).appointmentId(appointmentId).patientId(patientId).doctorId(doctorId)
                .chiefComplaint("Original complaint")
                .assessment("Original assessment")
                .build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(existing));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateConsultationRequest request = new UpdateConsultationRequest();
        request.setAssessment("Updated assessment");
        // chiefComplaint left null on the request - must not clear the existing value.

        ConsultationResponse response = consultationService.upsert(appointmentId, doctorUser, request);

        assertThat(response.getChiefComplaint()).isEqualTo("Original complaint");
        assertThat(response.getAssessment()).isEqualTo("Updated assessment");
    }

    @Test
    @DisplayName("upsert: forbidden for a doctor who does not own the appointment")
    void upsert_wrongDoctor_forbidden() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        doThrow(new BusinessException("FORBIDDEN", "not your appointment"))
                .when(authorizationService).assertDoctorOwnsAppointment(appointment, doctorUser);

        UpdateConsultationRequest request = new UpdateConsultationRequest();
        assertThatThrownBy(() -> consultationService.upsert(appointmentId, doctorUser, request))
                .isInstanceOf(BusinessException.class);
        verify(consultationRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPatientHistory: delegates to the authorization service and returns the page")
    void getPatientHistory_returnsPage() {
        Consultation consultation = Consultation.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(consultationRepository.findByPatientIdOrderByCreatedAtDesc(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(consultation)));

        Page<ConsultationResponse> page = consultationService.getPatientHistory(patientId, doctorUser, 0, 20);

        verify(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMyHistory: resolves the caller's own patient id and reuses getPatientHistory")
    void getMyHistory_resolvesOwnPatientId() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(consultationRepository.findByPatientIdOrderByCreatedAtDesc(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        consultationService.getMyHistory(patientUser, 0, 20);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
    }

    @Test
    @DisplayName("getMyHistory: a user with no patient profile gets a clear not-found error")
    void getMyHistory_noProfile_throws() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(null);

        assertThatThrownBy(() -> consultationService.getMyHistory(patientUser, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
