package com.mediwise.medicalrecord.controller;

import com.mediwise.auth.model.User;
import com.mediwise.medicalrecord.dto.MedicalDocumentResponse;
import com.mediwise.medicalrecord.model.MedicalDocument;
import com.mediwise.medicalrecord.service.MedicalDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MedicalDocumentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MedicalDocumentService medicalDocumentService;

    @InjectMocks
    private MedicalDocumentController medicalDocumentController;

    private User patientUser;
    private UUID patientId;
    private MedicalDocumentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(medicalDocumentController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        patientId = UUID.randomUUID();
        patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setRole(User.Role.PATIENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(patientUser, null, List.of()));

        sampleResponse = MedicalDocumentResponse.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .documentType(MedicalDocument.DocumentType.LAB_REPORT)
                .originalFilename("bloodwork.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .url("https://s3.example.com/presigned-url")
                .uploadedAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/medical-documents uploads a document and returns a presigned URL")
    void upload_returnsCreatedDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bloodwork.pdf", "application/pdf", "dummy-pdf-bytes".getBytes());

        when(medicalDocumentService.upload(
                eq(patientId), any(User.class), any(MultipartFile.class),
                eq(MedicalDocument.DocumentType.LAB_REPORT), isNull(), isNull()))
                .thenReturn(sampleResponse);

        mockMvc.perform(multipart("/api/v1/medical-documents")
                        .file(file)
                        .param("patientId", patientId.toString())
                        .param("documentType", "LAB_REPORT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("bloodwork.pdf"))
                .andExpect(jsonPath("$.data.url").value("https://s3.example.com/presigned-url"));
    }

    @Test
    @DisplayName("GET /api/v1/medical-documents/patient/{patientId} lists that patient's documents")
    void listForPatient_returnsList() throws Exception {
        when(medicalDocumentService.listForPatient(eq(patientId), any(User.class)))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/medical-documents/patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/v1/medical-documents/{id} returns the document with a fresh presigned URL")
    void getById_returnsDocument() throws Exception {
        UUID id = sampleResponse.getId();
        when(medicalDocumentService.getById(eq(id), any(User.class))).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/medical-documents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }
}
