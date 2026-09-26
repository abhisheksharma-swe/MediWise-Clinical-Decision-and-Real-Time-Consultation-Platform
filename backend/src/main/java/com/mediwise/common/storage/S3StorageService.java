package com.mediwise.common.storage;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.mediwise.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;

/**
 * Shared S3 upload/read logic, extracted from the near-identical blocks that used to live
 * separately in ProfileService and ChatService. Both of those upload paths return a permanently
 * public URL (matching the bucket's public-read policy, see AwsConfig); new, genuinely sensitive
 * content (medical documents) should use {@link #generatePresignedUrl} instead so it is never
 * readable from a bare, unauthenticated URL.
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
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            amazonS3.putObject(bucket, key, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }
}
