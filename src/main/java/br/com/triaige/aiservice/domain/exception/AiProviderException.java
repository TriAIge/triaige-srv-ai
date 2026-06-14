package br.com.triaige.aiservice.domain.exception;

import br.com.triaige.aiservice.domain.model.ErrorCode;
import lombok.Getter;

@Getter
public class AiProviderException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Boolean retryableOverride;

    public AiProviderException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }
    public AiProviderException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, null);
    }

    /**
     * @param retryableOverride permite sobrescrever, por instância, o retryable padrão do ErrorCode
     *                          (ex.: erro 4xx permanente do Gemini, que não deve ser retentado
     *                          mesmo usando o ErrorCode GEMINI_HTTP_ERROR).
     */
    public AiProviderException(ErrorCode errorCode, String message, Throwable cause, Boolean retryableOverride) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryableOverride = retryableOverride;
    }

    public boolean isRetryable() {
        return retryableOverride != null ? retryableOverride : errorCode.isRetryable();
    }
}
