package com.mediwise.followup.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediwise.auth.model.User;
import com.mediwise.followup.dto.CreateFollowUpRequest;
import com.mediwise.followup.dto.FollowUpResponse;
import com.mediwise.followup.model.FollowUp;
import com.mediwise.followup.service.FollowUpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FollowUpControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private FollowUpService followUpService;

    @InjectMocks
    private FollowUpController followUpController;

    private User doctorUser;
    private UUID consultationId;
    private FollowUpResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(followUpController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setRole(User.Role.DOCTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctorUser, null, List.of()));

        consultationId = UUID.randomUUID();
        sampleResponse = FollowUpResponse.builder()
                .id(UUID.randomUUID())
                .consultationId(consultationId)
                .patientId(UUID.randomUUID())
                .doctorId(UUID.randomUUID())
                .recommendedDate(LocalDate.now().plusWeeks(2))
                .reason("Re-check blood pressure")
                .status(FollowUp.Status.PENDING)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/consultations/{consultationId}/follow-ups creates a follow-up recommendation")
    void create_returnsCreatedFollowUp() throws Exception {
        CreateFollowUpRequest request = new CreateFollowUpRequest();
        request.setReason("Re-check blood pressure");

        when(followUpService.create(eq(consultationId), any(User.class), any(CreateFollowUpRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/consultations/" + consultationId + "/follow-ups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reason").value("Re-check blood pressure"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/follow-ups/me returns the caller's own follow-ups")
    void getMyFollowUps_returnsList() throws Exception {
        when(followUpService.getMyFollowUps(any(User.class))).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/follow-ups/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/v1/follow-ups/patient/{patientId} returns that patient's follow-ups")
    void getForPatient_returnsList() throws Exception {
        UUID patientId = sampleResponse.getPatientId();
        when(followUpService.getForPatient(eq(patientId), any(User.class))).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/follow-ups/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].patientId").value(patientId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/follow-ups/{id}/dismiss dismisses the follow-up")
    void dismiss_dismissesFollowUp() throws Exception {
        UUID id = sampleResponse.getId();
        FollowUpResponse dismissed = FollowUpResponse.builder()
                .id(id).status(FollowUp.Status.DISMISSED).build();
        when(followUpService.dismiss(eq(id), any(User.class))).thenReturn(dismissed);

        mockMvc.perform(patch("/api/v1/follow-ups/" + id + "/dismiss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }
}
