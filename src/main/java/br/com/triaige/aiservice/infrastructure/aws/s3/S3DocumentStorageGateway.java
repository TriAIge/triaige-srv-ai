package br.com.triaige.aiservice.infrastructure.aws.s3;

import br.com.triaige.aiservice.application.port.out.DocumentStorageGateway;
import br.com.triaige.aiservice.domain.exception.DocumentNotFoundException;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import br.com.triaige.aiservice.domain.exception.AiProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentStorageGateway implements DocumentStorageGateway {

    private final S3Client s3Client;

    @Override
    public String getText(String bucket, String key) {
        log.debug("Fetching document from S3: s3://{}/{}", bucket, key);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (InputStream is = s3Client.getObject(request)) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.debug("Fetched {} chars from s3://{}/{}", content.length(), bucket, key);
                return content;
            }

        } catch (NoSuchKeyException e) {
            log.warn("Document not found: s3://{}/{}", bucket, key);
            throw new DocumentNotFoundException(bucket, key);

        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                log.error("S3 access denied: s3://{}/{}", bucket, key);
                throw new AiProviderException(ErrorCode.S3_ACCESS_DENIED,
                        "S3 access denied for key: " + key, e);
            }
            log.error("S3 error fetching s3://{}/{}: {}", bucket, key, e.getMessage());
            throw new AiProviderException(ErrorCode.DOCUMENT_NOT_FOUND,
                    "S3 error: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Unexpected error fetching s3://{}/{}", bucket, key, e);
            throw new AiProviderException(ErrorCode.DOCUMENT_NOT_FOUND,
                    "Unexpected S3 error: " + e.getMessage(), e);
        }
    }
}
