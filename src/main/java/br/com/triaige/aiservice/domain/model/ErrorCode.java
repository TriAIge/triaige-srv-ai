package br.com.triaige.aiservice.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_MESSAGE(false),
    DOCUMENT_NOT_FOUND(false),
    EMPTY_DOCUMENT(false),
    S3_ACCESS_DENIED(false),
    GEMINI_TIMEOUT(true),
    GEMINI_HTTP_ERROR(true),
    GEMINI_INVALID_RESPONSE(true),
    JURISPRUDENCE_PROVIDER_ERROR(true),
    OUT_OF_SCOPE_LEGAL_AREA(false);

    private final boolean retryable;
}
