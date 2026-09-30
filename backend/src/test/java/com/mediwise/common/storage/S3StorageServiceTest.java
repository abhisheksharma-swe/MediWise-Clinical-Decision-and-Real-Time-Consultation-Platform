package com.mediwise.common.storage;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.mediwise.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Proves the retry behavior actually works, rather than trusting that adding
 * a Resilience4j-decorated call is enough on its own — the retry is
 * implemented programmatically (not via annotation-driven AOP) specifically
 * so this can be verified with a plain mocked AmazonS3, no Spring context
 * required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageService — upload retry behavior")
class S3StorageServiceTest {

    @Mock
    private AmazonS3 amazonS3;

    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3StorageService = new S3StorageService(amazonS3);
        ReflectionTestUtils.setField(s3StorageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3StorageService, "s3BaseUrl", "https://test-bucket.s3.amazonaws.com");
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", "test-content".getBytes());
    }

    @Test
    @DisplayName("succeeds on the first attempt without retrying when S3 is healthy")
    void uploadPrivate_succeedsFirstTry_noRetry() {
        s3StorageService.uploadPrivate("medical-documents/abc.pdf", sampleFile());

        verify(amazonS3, times(1)).putObject(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("recovers after transient AWS failures by retrying with a fresh stream each time")
    void uploadPrivate_transientFailureThenSuccess_retriesAndSucceeds() {
        when(amazonS3.putObject(anyString(), anyString(), any(), any()))
                .thenThrow(new SdkClientException("connection reset"))
                .thenThrow(new SdkClientException("connection reset"))
                .thenReturn(null);

        s3StorageService.uploadPrivate("medical-documents/abc.pdf", sampleFile());

        verify(amazonS3, times(3)).putObject(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("gives up after exhausting retries and wraps the failure as a BusinessException")
    void uploadPrivate_persistentFailure_exhaustsRetriesAndThrowsBusinessException() {
        when(amazonS3.putObject(anyString(), anyString(), any(), any()))
                .thenThrow(new SdkClientException("S3 unreachable"));

        assertThatThrownBy(() -> s3StorageService.uploadPrivate("medical-documents/abc.pdf", sampleFile()))
                .isInstanceOf(BusinessException.class);

        verify(amazonS3, times(3)).putObject(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("uploadPublic returns the expected public URL on success")
    void uploadPublic_returnsPublicUrl() {
        String url = s3StorageService.uploadPublic("profile-images/abc.jpg", sampleFile());

        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/profile-images/abc.jpg");
    }
}
