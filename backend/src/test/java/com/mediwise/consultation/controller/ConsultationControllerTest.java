package com.mediwise.consultation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediwise.auth.model.User;
import com.mediwise.consultation.dto.ConsultationResponse;
import com.mediwise.consultation.dto.UpdateConsultationRequest;
import com.mediwise.consultation.service.ConsultationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsultationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ConsultationService consultationService;

    @InjectMocks
    private ConsultationController consultationController;

    private User doctorUser;
    private UUID appointmentId;
    private ConsultationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(consultationController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setRole(User.Role.DOCTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctorUser, null, List.of()));

        appointmentId = UUID.randomUUID();
        sampleResponse = ConsultationResponse.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .patientId(UUID.randomUUID())
                .doctorId(UUID.randomUUID())
                .chiefComplaint("Persistent cough")
                .symptoms(List.of("cough", "fatigue"))
                .status("IN_PROGRESS")
                .createdAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/consultations/{appointmentId} returns the consultation record")
    void getForAppointment_returnsRecord() throws Exception {
        when(consultationService.getForAppointment(eq(appointmentId), any(User.class))).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/consultations/" + appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chiefComplaint").value("Persistent cough"))
                .andExpect(jsonPath("$.data.symptoms[0]").value("cough"));
    }

    @Test
    @DisplayName("GET /api/v1/consultations/{appointmentId} returns null data (not an error) when none recorded yet")
    void getForAppointment_none_returnsNullData() throws Exception {
        when(consultationService.getForAppointment(eq(appointmentId), any(User.class))).thenReturn(null);

        mockMvc.perform(get("/api/v1/consultations/" + appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/v1/consultations/{appointmentId} saves clinical content and returns it")
    void upsert_savesAndReturnsConsultation() throws Exception {
        UpdateConsultationRequest request = new UpdateConsultationRequest();
        request.setChiefComplaint("Persistent cough");
        request.setSymptoms(List.of("cough", "fatigue"));

        when(consultationService.upsert(eq(appointmentId), any(User.class), any(UpdateConsultationRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/consultations/" + appointmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chiefComplaint").value("Persistent cough"));
    }

    @Test
    @DisplayName("GET /api/v1/consultations/me returns the caller's own consultation history")
    void getMyHistory_returnsPage() throws Exception {
        when(consultationService.getMyHistory(any(User.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/consultations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].appointmentId").value(appointmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/consultations/patient/{patientId} returns that patient's history")
    void getPatientHistory_returnsPage() throws Exception {
        UUID patientId = sampleResponse.getPatientId();
        when(consultationService.getPatientHistory(eq(patientId), any(User.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/consultations/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)));
    }
}
