package br.com.triaige.aiservice.domain.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String bucket, String key) {
        super(String.format("Document not found in S3: s3://%s/%s", bucket, key));
    }
}
