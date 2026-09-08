package com.mediwise.config;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AwsConfig {

    @Value("${application.aws.access-key}")
    private String accessKey;

    @Value("${application.aws.secret-key}")
    private String secretKey;

    @Value("${application.aws.region}")
    private String region;

    /** Set only for a local/dev S3-compatible store (e.g. MinIO) — real AWS S3 is used whenever this is blank. */
    @Value("${application.aws.s3.endpoint}")
    private String endpoint;

    @Value("${application.aws.s3.bucket}")
    private String bucket;

    @Bean
    public AmazonS3 amazonS3() {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();

        if (!accessKey.isEmpty()) {
            builder.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)));
        }

        if (!endpoint.isEmpty()) {
            // Path-style addressing (http://host:port/bucket/key) rather than AWS's default
            // virtual-hosted style (http://bucket.host/key) — MinIO and most self-hosted
            // S3-compatible stores don't do per-bucket DNS/vhost routing.
            AmazonS3 client = builder
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                    .withPathStyleAccessEnabled(true)
                    .build();
            ensureBucketExists(client);
            return client;
        }

        return builder.withRegion(region).build();
    }

    /**
     * Only ever runs against the local/dev endpoint override above — real AWS S3 bucket
     * creation/policy is deliberately left as an infrastructure concern in production, not
     * something the app does automatically on startup.
     *
     * Sets a public-read bucket policy too: ProfileService/ChatService both hand back plain
     * `s3BaseUrl + "/" + key` URLs with no request-signing anywhere, so whatever bucket this
     * app writes to has to already be public-read for those URLs to actually load — true for
     * a real AWS bucket configured this way, and MinIO defaults to private, so this makes
     * local dev match that same assumption instead of every upload 403ing on read.
     */
    private void ensureBucketExists(AmazonS3 client) {
        try {
            if (!client.doesBucketExistV2(bucket)) {
                client.createBucket(bucket);
                log.info("Created local dev S3 bucket '{}' on {}", bucket, endpoint);
            }
            String publicReadPolicy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": "*",
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(bucket);
            client.setBucketPolicy(bucket, publicReadPolicy);
        } catch (Exception e) {
            log.warn("Could not verify/create local dev S3 bucket '{}': {}", bucket, e.getMessage());
        }
    }
}
