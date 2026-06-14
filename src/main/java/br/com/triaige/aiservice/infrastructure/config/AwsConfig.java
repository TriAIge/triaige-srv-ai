package br.com.triaige.aiservice.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties props;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.getRegion()));

        String endpoint = props.getS3().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            log.info("S3 using custom endpoint: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(localstackCredentials())
                   .forcePathStyle(props.getS3().isForcePathStyle());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private StaticCredentialsProvider localstackCredentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test"));
    }
}
