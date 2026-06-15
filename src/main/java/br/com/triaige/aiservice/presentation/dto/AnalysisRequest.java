package br.com.triaige.aiservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO de transporte para a requisição REST de análise de IA,
 * recebida pelo orquestrador. Mantido propositalmente separado do modelo de domínio.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisRequest {

    private String eventType;
    private String correlationId;
    private String sessionId;
    private String caseId;
    private String tenantId;
    private String legalArea;
    private String s3Bucket;
    private List<DocumentRef> documents;
    private List<String> requestedTools;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentRef {
        private String documentId;
        private String documentType;
        private String s3Key;
    }
}
