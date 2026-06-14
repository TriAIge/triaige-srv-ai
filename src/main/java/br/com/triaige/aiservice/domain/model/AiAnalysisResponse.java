package br.com.triaige.aiservice.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAnalysisResponse {

    private String correlationId;
    private String sessionId;
    private String caseId;
    private String tenantId;
    private AnalysisStatus status;

    // Presente quando status == COMPLETED ou OUT_OF_SCOPE
    private Analysis analysis;

    // Presente quando status == FAILED
    private AnalysisError error;

    private Metadata metadata;

    // ── Tipos aninhados ──────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Analysis {
        private String legalArea;
        private String caseSummary;
        private List<Party> parties;
        private List<String> claims;
        private List<Evidence> evidences;
        private List<Risk> risks;
        private List<String> missingInformation;
        private Priority priority;
        private List<JurisprudenceResult> jurisprudence;
        private List<String> warnings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Party {
        private String name;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String documentId;
        private String description;
        private String relevance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {
        private String description;
        private String severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Priority {
        private String level;
        private String justification;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnalysisError {
        private String code;
        private String message;
        private boolean retryable;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        private String model;
        private Instant processedAt;
        private Integer documentsAnalyzed;
        private List<String> toolsUsed;
        private Long geminiCallDurationMs;
    }
}
