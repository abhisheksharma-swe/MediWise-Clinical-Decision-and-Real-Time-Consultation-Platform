package com.mediwise.medicalrecord.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediwise.auth.model.User;
import com.mediwise.medicalrecord.dto.ConditionResponse;
import com.mediwise.medicalrecord.dto.CreateConditionRequest;
import com.mediwise.medicalrecord.dto.MedicalRecordResponse;
import com.mediwise.medicalrecord.dto.UpdateConditionStatusRequest;
import com.mediwise.medicalrecord.model.PatientCondition;
import com.mediwise.medicalrecord.service.MedicalRecordService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MedicalRecordControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private MedicalRecordService medicalRecordService;

    @InjectMocks
    private MedicalRecordController medicalRecordController;

    private User patientUser;
    private UUID patientId;
    private MedicalRecordResponse sampleRecord;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(medicalRecordController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        patientId = UUID.randomUUID();
        patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setRole(User.Role.PATIENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(patientUser, null, List.of()));

        sampleRecord = MedicalRecordResponse.builder()
                .patientId(patientId)
                .conditions(List.of())
                .allergies(List.of())
                .medications(List.of())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/medical-records/me returns the caller's own record")
    void getMyRecord_returnsRecord() throws Exception {
        when(medicalRecordService.getMyRecord(any(User.class))).thenReturn(sampleRecord);

        mockMvc.perform(get("/api/v1/medical-records/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientId").value(patientId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/medical-records/me/conditions adds a self-reported condition")
    void addMyCondition_addsCondition() throws Exception {
        CreateConditionRequest request = new CreateConditionRequest();
        request.setName("Asthma");

        ConditionResponse conditionResponse = ConditionResponse.builder()
                .id(UUID.randomUUID()).name("Asthma").status(PatientCondition.Status.ACTIVE).selfReported(true)
                .build();

        when(medicalRecordService.resolveOwnPatientId(any(User.class))).thenReturn(patientId);
        when(medicalRecordService.addCondition(eq(patientId), any(User.class), any(CreateConditionRequest.class)))
                .thenReturn(conditionResponse);

        mockMvc.perform(post("/api/v1/medical-records/me/conditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Asthma"))
                .andExpect(jsonPath("$.data.selfReported").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/medical-records/{patientId} returns that patient's record")
    void getRecord_returnsRecord() throws Exception {
        when(medicalRecordService.getRecord(eq(patientId), any(User.class))).thenReturn(sampleRecord);

        mockMvc.perform(get("/api/v1/medical-records/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientId").value(patientId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/medical-records/conditions/{id} updates the condition's status")
    void updateConditionStatus_updatesStatus() throws Exception {
        UUID conditionId = UUID.randomUUID();
        UpdateConditionStatusRequest request = new UpdateConditionStatusRequest();
        request.setStatus(PatientCondition.Status.RESOLVED);

        ConditionResponse updated = ConditionResponse.builder()
                .id(conditionId).name("Asthma").status(PatientCondition.Status.RESOLVED).build();

        when(medicalRecordService.updateConditionStatus(eq(conditionId), any(User.class), any(UpdateConditionStatusRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/medical-records/conditions/" + conditionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }
}
