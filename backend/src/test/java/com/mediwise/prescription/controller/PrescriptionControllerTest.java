package com.mediwise.prescription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediwise.auth.model.User;
import com.mediwise.prescription.dto.CreatePrescriptionRequest;
import com.mediwise.prescription.dto.PrescriptionItemRequest;
import com.mediwise.prescription.dto.PrescriptionResponse;
import com.mediwise.prescription.service.PrescriptionService;
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
class PrescriptionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PrescriptionService prescriptionService;

    @InjectMocks
    private PrescriptionController prescriptionController;

    private User doctorUser;
    private UUID consultationId;
    private PrescriptionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(prescriptionController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setRole(User.Role.DOCTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctorUser, null, List.of()));

        consultationId = UUID.randomUUID();
        sampleResponse = PrescriptionResponse.builder()
                .id(UUID.randomUUID())
                .consultationId(consultationId)
                .patientId(UUID.randomUUID())
                .doctorId(UUID.randomUUID())
                .notes("Take with food")
                .items(List.of())
                .createdAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/consultations/{consultationId}/prescriptions creates a prescription")
    void create_returnsCreatedPrescription() throws Exception {
        CreatePrescriptionRequest request = new CreatePrescriptionRequest();
        PrescriptionItemRequest item = new PrescriptionItemRequest();
        item.setMedicineName("Paracetamol");
        request.setItems(List.of(item));

        when(prescriptionService.create(eq(consultationId), any(User.class), any(CreatePrescriptionRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/consultations/" + consultationId + "/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notes").value("Take with food"));
    }

    @Test
    @DisplayName("GET /api/v1/prescriptions/{id} returns the prescription")
    void getById_returnsPrescription() throws Exception {
        UUID id = sampleResponse.getId();
        when(prescriptionService.getById(eq(id), any(User.class))).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/prescriptions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/prescriptions/me returns the caller's own prescriptions")
    void getMyPrescriptions_returnsPage() throws Exception {
        when(prescriptionService.getMyPrescriptions(any(User.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/prescriptions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].consultationId").value(consultationId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/prescriptions/patient/{patientId} returns that patient's prescriptions")
    void getForPatient_returnsPage() throws Exception {
        UUID patientId = sampleResponse.getPatientId();
        when(prescriptionService.getForPatient(eq(patientId), any(User.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/prescriptions/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)));
    }
}
