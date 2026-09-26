package com.mediwise.common.security;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the shared ownership checks extracted from AppointmentService/AiService — every new
 * clinical domain (consultation, prescription, medical record, follow-up) depends on this logic
 * behaving identically to the checks it replaced (see §21 of the implementation plan).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentAuthorizationService")
class AppointmentAuthorizationServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;

    @InjectMocks
    private AppointmentAuthorizationService authorizationService;

    private UUID patientId;
    private UUID doctorId;
    private UUID patientUserId;
    private UUID doctorUserId;
    private User patientUser;
    private User doctorUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        patientUserId = UUID.randomUUID();
        doctorUserId = UUID.randomUUID();
        patientUser = User.builder().id(patientUserId).role(User.Role.PATIENT).build();
        doctorUser = User.builder().id(doctorUserId).role(User.Role.DOCTOR).build();
        adminUser = User.builder().id(UUID.randomUUID()).role(User.Role.ADMIN).build();
    }

    // ── assertCanViewPatientHistory ────────────────────────────────────────

    @Test
    @DisplayName("admin can always view patient history")
    void viewHistory_adminAllowed() {
        assertThatCode(() -> authorizationService.assertCanViewPatientHistory(patientId, adminUser))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("patient can view their own history")
    void viewHistory_ownPatientAllowed() {
        lenient().when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));

        assertThatCode(() -> authorizationService.assertCanViewPatientHistory(patientId, patientUser))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a patient cannot view another patient's history")
    void viewHistory_otherPatientForbidden() {
        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(UUID.randomUUID()).userId(patientUserId).build()));

        assertThatThrownBy(() -> authorizationService.assertCanViewPatientHistory(patientId, patientUser))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("a doctor with an existing appointment relationship can view the patient's history")
    void viewHistory_relatedDoctorAllowed() {
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).userId(doctorUserId).build()));
        when(appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)).thenReturn(true);

        assertThatCode(() -> authorizationService.assertCanViewPatientHistory(patientId, doctorUser))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a doctor with no appointment relationship to the patient is forbidden")
    void viewHistory_unrelatedDoctorForbidden() {
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).userId(doctorUserId).build()));
        when(appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)).thenReturn(false);

        assertThatThrownBy(() -> authorizationService.assertCanViewPatientHistory(patientId, doctorUser))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── assertCanAccessAppointment ─────────────────────────────────────────

    @Test
    @DisplayName("the patient on the appointment can access it")
    void accessAppointment_ownerPatientAllowed() {
        Appointment appt = Appointment.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        lenient().when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(patientId).userId(patientUserId).build()));

        assertThatCode(() -> authorizationService.assertCanAccessAppointment(appt, patientUser))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a stranger patient cannot access someone else's appointment")
    void accessAppointment_strangerPatientForbidden() {
        Appointment appt = Appointment.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(patientProfileRepository.findByUserId(patientUserId))
                .thenReturn(Optional.of(PatientProfile.builder().id(UUID.randomUUID()).userId(patientUserId).build()));

        assertThatThrownBy(() -> authorizationService.assertCanAccessAppointment(appt, patientUser))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("the assigned doctor can access the appointment")
    void accessAppointment_assignedDoctorAllowed() {
        Appointment appt = Appointment.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).userId(doctorUserId).build()));

        assertThatCode(() -> authorizationService.assertCanAccessAppointment(appt, doctorUser))
                .doesNotThrowAnyException();
    }

    // ── assertDoctorOwnsAppointment ────────────────────────────────────────

    @Test
    @DisplayName("returns the doctor's id when they own the appointment")
    void doctorOwns_returnsDoctorId() {
        Appointment appt = Appointment.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(doctorId).userId(doctorUserId).build()));

        UUID resolved = authorizationService.assertDoctorOwnsAppointment(appt, doctorUser);

        assertThat(resolved).isEqualTo(doctorId);
    }

    @Test
    @DisplayName("forbids a doctor who is not assigned to the appointment")
    void doctorOwns_wrongDoctorForbidden() {
        Appointment appt = Appointment.builder().id(UUID.randomUUID()).patientId(patientId).doctorId(doctorId).build();
        when(doctorRepository.findByUserId(doctorUserId))
                .thenReturn(Optional.of(Doctor.builder().id(UUID.randomUUID()).userId(doctorUserId).build()));

        assertThatThrownBy(() -> authorizationService.assertDoctorOwnsAppointment(appt, doctorUser))
                .isInstanceOf(BusinessException.class);
    }
}
