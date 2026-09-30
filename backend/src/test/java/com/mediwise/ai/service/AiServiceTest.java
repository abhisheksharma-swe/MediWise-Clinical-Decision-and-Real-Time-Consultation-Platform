package com.mediwise.ai.service;

import com.mediwise.ai.dto.AiReportResponse;
import com.mediwise.ai.dto.SymptomLogRequest;
import com.mediwise.ai.model.AiReport;
import com.mediwise.ai.repository.AiReportRepository;
import com.mediwise.ai.repository.SymptomLogRepository;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.common.security.AppointmentAuthorizationService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * No Gemini API key is configured in these tests, so every call exercises the
 * safe-fallback path (never a real network call) — this is the same behavior
 * production falls back to when Gemini is unreachable or misconfigured, and
 * it's the one path that's fully deterministic to assert on.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiService — Fallback Triage & Access Control")
@SuppressWarnings("unchecked")
class AiServiceTest {

    @Mock private AiReportRepository aiReportRepository;
    @Mock private SymptomLogRepository symptomLogRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private AiService aiService;

    private User patientUser;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiService, "geminiApiKey", "");

        patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setRole(User.Role.PATIENT);

        patientId = UUID.randomUUID();

        lenient().when(aiReportRepository.save(any())).thenAnswer(inv -> {
            AiReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID().toString());
            return r;
        });
    }

    private SymptomLogRequest request(List<String> symptoms) {
        SymptomLogRequest req = new SymptomLogRequest();
        req.setPatientId(patientId);
        req.setSymptoms(symptoms);
        req.setSeverity("MODERATE");
        return req;
    }

    @Test
    @DisplayName("no Gemini key configured -> falls back instead of throwing")
    void analyzeSymptoms_noApiKey_fallsBackSafely() {
        PatientProfile profile = new PatientProfile();
        profile.setId(patientId);
        profile.setDob(LocalDate.now().minusYears(30));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(profile));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(profile));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        AiReportResponse response = aiService.analyzeSymptoms(request(List.of("mild headache")), patientUser);

        assertThat(response.getSuggestedSpecialty()).isEqualTo("General Medicine");
        assertThat(response.getConfidence()).isEqualTo(0.0);
        assertThat(response.getRecommendation()).contains("not a substitute for professional medical advice");
        verify(symptomLogRepository).save(any());
    }

    @Test
    @DisplayName("adult patient with no emergency symptoms -> Routine Consultation care category")
    void analyzeSymptoms_adultRoutineCase_defaultsToRoutineCare() {
        PatientProfile profile = new PatientProfile();
        profile.setId(patientId);
        profile.setDob(LocalDate.now().minusYears(40));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(profile));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(profile));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        AiReportResponse response = aiService.analyzeSymptoms(request(List.of("mild cough")), patientUser);

        assertThat(response.getCareCategory()).isEqualTo("Routine Consultation");
    }

    @Test
    @DisplayName("pediatric patient (under 18) -> Pediatric Care category, server-computed age never client-supplied")
    void analyzeSymptoms_pediatricPatient_routesToPediatricCare() {
        PatientProfile profile = new PatientProfile();
        profile.setId(patientId);
        profile.setDob(LocalDate.now().minusYears(10));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(profile));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(profile));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        AiReportResponse response = aiService.analyzeSymptoms(request(List.of("mild fever")), patientUser);

        assertThat(response.getCareCategory()).isEqualTo("Pediatric Care");
    }

    @Test
    @DisplayName("emergency keyword forces urgencyScore=100 + Emergency Medicine + Urgent Care regardless of age")
    void analyzeSymptoms_emergencyKeyword_forcesEmergencyOverride() {
        PatientProfile profile = new PatientProfile();
        profile.setId(patientId);
        profile.setDob(LocalDate.now().minusYears(40));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(profile));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(profile));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        AiReportResponse response = aiService.analyzeSymptoms(request(List.of("severe chest pain")), patientUser);

        assertThat(response.getUrgencyScore()).isEqualTo(100);
        assertThat(response.getSuggestedSpecialty()).isEqualTo("Emergency Medicine");
        assertThat(response.getCareCategory()).isEqualTo("Urgent Care");
        assertThat(response.getRecommendation()).contains("seek emergency care immediately");
    }

    @Test
    @DisplayName("patient logging symptoms for another patient (no shared appointment) is denied")
    void analyzeSymptoms_patientForAnotherPatient_throwsUnauthorized() {
        UUID otherPatientId = UUID.randomUUID();
        when(patientProfileRepository.findByUserId(patientUser.getId()))
                .thenReturn(Optional.of(patientProfileWithId(otherPatientId)));

        SymptomLogRequest req = request(List.of("headache"));
        req.setPatientId(patientId);

        assertThatThrownBy(() -> aiService.analyzeSymptoms(req, patientUser))
                .isInstanceOf(UnauthorizedException.class);
        verify(symptomLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("doctor with a real appointment for the patient can log symptoms on their behalf")
    void analyzeSymptoms_doctorWithMatchingAppointment_allowed() {
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setRole(User.Role.DOCTOR);

        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        Appointment appointment = new Appointment();
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        when(doctorRepository.findByUserId(doctorUser.getId())).thenReturn(Optional.of(doctor));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(patientProfileWithId(patientId)));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        SymptomLogRequest req = request(List.of("mild headache"));
        req.setAppointmentId(appointmentId);

        AiReportResponse response = aiService.analyzeSymptoms(req, doctorUser);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("doctor without a matching appointment for the patient is denied")
    void analyzeSymptoms_doctorWithoutMatchingAppointment_throwsUnauthorized() {
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setRole(User.Role.DOCTOR);

        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setPatientId(UUID.randomUUID()); // different patient
        appointment.setDoctorId(UUID.randomUUID());
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        SymptomLogRequest req = request(List.of("mild headache"));
        req.setAppointmentId(appointmentId);

        assertThatThrownBy(() -> aiService.analyzeSymptoms(req, doctorUser))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("admin can log symptoms for any patient")
    void analyzeSymptoms_admin_bypassesOwnershipCheck() {
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(User.Role.ADMIN);
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(patientProfileWithId(patientId)));
        when(doctorRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        AiReportResponse response = aiService.analyzeSymptoms(request(List.of("mild headache")), adminUser);

        assertThat(response).isNotNull();
        verifyNoInteractions(authorizationService);
    }

    @Test
    @DisplayName("getLatestReport returns null (not an error) when the patient has no reports yet")
    void getLatestReport_none_returnsNull() {
        when(aiReportRepository.findTopByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(Optional.empty());

        AiReportResponse response = aiService.getLatestReport(patientId, patientUser);

        assertThat(response).isNull();
        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
    }

    private PatientProfile patientProfileWithId(UUID id) {
        PatientProfile profile = new PatientProfile();
        profile.setId(id);
        profile.setDob(LocalDate.now().minusYears(30));
        return profile;
    }
}
