package com.mediwise.medicalrecord.service;

import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.security.AppointmentAuthorizationService;
import com.mediwise.common.storage.S3StorageService;
import com.mediwise.medicalrecord.dto.MedicalDocumentResponse;
import com.mediwise.medicalrecord.model.MedicalDocument;
import com.mediwise.medicalrecord.repository.MedicalDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicalDocumentService")
class MedicalDocumentServiceTest {

    @Mock private MedicalDocumentRepository documentRepository;
    @Mock private S3StorageService s3StorageService;
    @Mock private AppointmentAuthorizationService authorizationService;

    @InjectMocks
    private MedicalDocumentService medicalDocumentService;

    private UUID patientId;
    private User patientUser;
    private User doctorUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        patientUser = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        doctorUser = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
        adminUser = User.builder().id(UUID.randomUUID()).role(User.Role.ADMIN).build();
        ReflectionTestUtils.setField(medicalDocumentService, "presignedUrlTtlMinutes", 15L);
    }

    private MultipartFile jpegFile() {
        return new MockMultipartFile("file", "scan.jpg", "image/jpeg", "fake-bytes".getBytes());
    }

    @Test
    @DisplayName("upload: a patient can upload a document to their own record")
    void upload_ownPatient_success() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        when(documentRepository.save(any(MedicalDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(s3StorageService.generatePresignedUrl(anyString(), any())).thenReturn("https://signed-url");
        MultipartFile file = jpegFile();

        MedicalDocumentResponse response = medicalDocumentService.upload(
                patientId, patientUser, file, MedicalDocument.DocumentType.LAB_REPORT, null, null);

        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getUrl()).isEqualTo("https://signed-url");
        verify(s3StorageService).uploadPrivate(anyString(), eq(file));
    }

    @Test
    @DisplayName("upload: a patient cannot upload to someone else's record")
    void upload_otherPatient_forbidden() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> medicalDocumentService.upload(
                patientId, patientUser, jpegFile(), MedicalDocument.DocumentType.LAB_REPORT, null, null))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("upload: a doctor with no relationship to the patient is forbidden")
    void upload_unrelatedDoctor_forbidden() {
        doThrow(new BusinessException("FORBIDDEN", "no relationship"))
                .when(authorizationService).assertCanViewPatientHistory(patientId, doctorUser);

        assertThatThrownBy(() -> medicalDocumentService.upload(
                patientId, doctorUser, jpegFile(), MedicalDocument.DocumentType.LAB_REPORT, null, null))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("upload: an admin cannot upload medical documents")
    void upload_admin_forbidden() {
        assertThatThrownBy(() -> medicalDocumentService.upload(
                patientId, adminUser, jpegFile(), MedicalDocument.DocumentType.LAB_REPORT, null, null))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("upload: rejected for an unsupported content type")
    void upload_invalidContentType_rejected() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        MultipartFile textFile = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> medicalDocumentService.upload(
                patientId, patientUser, textFile, MedicalDocument.DocumentType.OTHER, null, null))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("upload: rejected for an oversized file")
    void upload_tooLarge_rejected() {
        when(authorizationService.resolvePatientId(patientUser)).thenReturn(patientId);
        MultipartFile oversized = new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[16 * 1024 * 1024]);

        assertThatThrownBy(() -> medicalDocumentService.upload(
                patientId, patientUser, oversized, MedicalDocument.DocumentType.LAB_REPORT, null, null))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("listForPatient: an authorized viewer gets the presigned document list")
    void listForPatient_returnsDocuments() {
        MedicalDocument doc = MedicalDocument.builder().id(UUID.randomUUID()).patientId(patientId)
                .documentType(MedicalDocument.DocumentType.OTHER).s3Key("key").build();
        when(documentRepository.findByPatientIdOrderByUploadedAtDesc(patientId)).thenReturn(List.of(doc));
        when(s3StorageService.generatePresignedUrl(anyString(), any())).thenReturn("https://signed-url");

        List<MedicalDocumentResponse> result = medicalDocumentService.listForPatient(patientId, patientUser);

        verify(authorizationService).assertCanViewPatientHistory(patientId, patientUser);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("https://signed-url");
    }
}
