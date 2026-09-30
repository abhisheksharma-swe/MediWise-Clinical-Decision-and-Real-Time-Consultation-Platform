package com.mediwise.common.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.mediwise.common.exception.BusinessException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;

/**
 * Shared S3 upload/read logic, extracted from the near-identical blocks that used to live
 * separately in ProfileService and ChatService. Both of those upload paths return a permanently
 * public URL (matching the bucket's public-read policy, see AwsConfig); new, genuinely sensitive
 * content (medical documents) should use {@link #generatePresignedUrl} instead so it is never
 * readable from a bare, unauthenticated URL.
 *
 * Uploads retry a handful of times on transient AWS SDK failures (network blips, throttling) —
 * the file's bytes are read into memory once up front specifically so each retry attempt can
 * open a fresh stream; retrying against an already-partially-consumed stream from a failed
 * attempt would silently upload a truncated file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final AmazonS3 amazonS3;

    @Value("${application.aws.s3.bucket}")
    private String bucket;

    @Value("${application.aws.s3.base-url}")
    private String s3BaseUrl;

    private final Retry uploadRetry = Retry.of("s3-upload", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(300))
            .retryExceptions(AmazonClientException.class)
            .build());

    /** Uploads a file to `key` and returns its permanently public URL. */
    public String uploadPublic(String key, MultipartFile file) {
        putObject(key, file);
        return s3BaseUrl + "/" + key;
    }

    /** Uploads a file to `key` without returning a public URL — read access is via {@link #generatePresignedUrl}. */
    public void uploadPrivate(String key, MultipartFile file) {
        putObject(key, file);
    }

    /** A time-limited, signed GET URL for content that must not be permanently public. */
    public String generatePresignedUrl(String key, Duration ttl) {
        Date expiration = new Date(System.currentTimeMillis() + ttl.toMillis());
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(request).toString();
    }

    private void putObject(String key, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(bytes.length);

        try {
            Retry.decorateRunnable(uploadRetry, () ->
                    amazonS3.putObject(bucket, key, new ByteArrayInputStream(bytes), metadata)
            ).run();
        } catch (AmazonClientException e) {
            log.error("S3 upload failed for key {} after retries: {}", key, e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }
}
