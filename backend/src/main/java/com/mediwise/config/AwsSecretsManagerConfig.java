package com.mediwise.config;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AwsSecretsManagerConfig {

    @Bean
    @Profile("prod")
        public AWSSecretsManager awsSecretsManager(
            @Value("${application.aws.region}") String awsRegion) {
        return AWSSecretsManagerClientBuilder.standard()
                .withRegion(awsRegion)
                .build();
    }
}