package br.com.triaige.aiservice.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String endpoint;
        private boolean forcePathStyle = false;
    }
}
