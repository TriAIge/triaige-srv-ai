package br.com.triaige.aiservice.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Representação interna de domínio de uma requisição de análise.
 * Construída a partir da requisição REST recebida após a validação.
 */
@Data
@Builder
public class AiAnalysisRequest {

    private String correlationId;
    private String sessionId;
    private String caseId;
    private String tenantId;
    private String legalArea;
    private String s3Bucket;
    private List<DocumentRef> documents;
    private List<String> requestedTools;
    private List<JurisprudenceResult> jurisprudence;

    @Data
    @Builder
    public static class DocumentRef {
        private String documentId;
        private String documentType;
        private String s3Key;
    }
}
