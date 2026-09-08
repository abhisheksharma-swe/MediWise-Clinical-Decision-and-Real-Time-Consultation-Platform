package com.mediwise.common.util;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtSecretProvider {

    private final AWSSecretsManager secretsManager;
    private final ObjectMapper objectMapper;

    @Value("${application.jwt.secret:}")
    private String configuredSecret;

    @Value("${application.jwt.secret-name:mediwise/prod/jwt}")
    private String secretName;

    private String resolvedSecret;

    public JwtSecretProvider(
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<AWSSecretsManager> secretsManagerProvider) {
        this.objectMapper = objectMapper;
        this.secretsManager = secretsManagerProvider.getIfAvailable();
    }

    @PostConstruct
    void initialize() {
        resolvedSecret = configuredSecret == null || configuredSecret.isBlank()
                ? loadFromSecretsManager()
                : configuredSecret.trim();
        validate(resolvedSecret);
    }

    public String getSecret() {
        return resolvedSecret;
    }

    private String loadFromSecretsManager() {
        if (secretsManager == null) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set for local development, or AWS Secrets Manager must be configured for production");
        }
        try {
            String value = secretsManager.getSecretValue(new GetSecretValueRequest()
                    .withSecretId(secretName)).getSecretString();
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("AWS JWT secret is empty: " + secretName);
            }
            return extractSecret(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Unable to load JWT secret from AWS Secrets Manager secret '" + secretName + "'", exception);
        }
    }

    private String extractSecret(String value) {
        if (!value.startsWith("{")) {
            return value;
        }
        try {
            JsonNode json = objectMapper.readTree(value);
            JsonNode secret = json.get("jwtSecret");
            if (secret == null) {
                secret = json.get("secret");
            }
            if (secret == null || secret.asText().isBlank()) {
                throw new IllegalStateException("AWS JWT secret JSON must contain 'jwtSecret'");
            }
            return secret.asText().trim();
        } catch (Exception exception) {
            throw new IllegalStateException("AWS JWT secret must be plain text or valid JSON", exception);
        }
    }

    private void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is missing");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes (256 bits)");
        }
    }
}