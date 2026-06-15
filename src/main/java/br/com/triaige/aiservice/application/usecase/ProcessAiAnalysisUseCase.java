package br.com.triaige.aiservice.application.usecase;

import br.com.triaige.aiservice.application.port.in.ProcessAiAnalysisPort;
import br.com.triaige.aiservice.application.port.out.*;
import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.exception.DocumentNotFoundException;
import br.com.triaige.aiservice.domain.exception.OutOfScopeLegalAreaException;
import br.com.triaige.aiservice.domain.model.*;
import br.com.triaige.aiservice.infrastructure.config.AppProperties;
import br.com.triaige.aiservice.infrastructure.gemini.GeminiPromptBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessAiAnalysisUseCase implements ProcessAiAnalysisPort {

    private final DocumentStorageGateway storageGateway;
    private final AiModelGateway aiModelGateway;
    private final JurisprudenceGateway jurisprudenceGateway;
    private final GeminiPromptBuilder promptBuilder;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;

    @Override
    public AiAnalysisResponse process(AiAnalysisRequest request) {
        Instant start = Instant.now();
        List<String> toolsUsed = new ArrayList<>();

        log.info("Starting AI analysis: sessionId={}, caseId={}, tenantId={}, correlationId={}",
                request.getSessionId(), request.getCaseId(),
                request.getTenantId(), request.getCorrelationId());

        try {
            // 1. Validar o escopo da área jurídica
            validateLegalArea(request.getLegalArea());

            // 2. Buscar o texto normalizado no S3
            List<LegalDocument> documents = fetchDocuments(request);
            toolsUsed.add("CASE_SUMMARY");

            // 3. Injetar o texto nos documentos da requisição
            injectTextContent(request, documents);

            // 4. Opcionalmente buscar jurisprudência
            List<JurisprudenceResult> jurisprudence = List.of();
            boolean jurisprudenceRequested = request.getRequestedTools() != null
                    && request.getRequestedTools().contains("JURISPRUDENCE_SEARCH");

            if (jurisprudenceRequested) {
                jurisprudence = searchJurisprudence(request);
                if (!jurisprudence.isEmpty()) {
                    toolsUsed.add("JURISPRUDENCE_SEARCH");
                }
            }

            // 5. Chamar o Gemini — medindo a duração
            List<JurisprudenceResult> jurisprudenceForGemini = jurisprudence;
            long geminiStart = System.currentTimeMillis();
            AiAnalysisResponse response = Timer.builder("gemini.call.duration")
                    .description("Gemini API call duration")
                    .register(meterRegistry)
                    .record(() -> callGemini(request, jurisprudenceForGemini));
            long geminiDuration = System.currentTimeMillis() - geminiStart;

            // 5.1 O próprio Gemini classifica a área jurídica do caso a partir dos
            // documentos. Se ele identificar que o caso está fora do escopo
            // (ex.: criminal), encerrar aqui sem produzir a análise completa.
            String identifiedLegalArea = response.getAnalysis() != null
                    ? response.getAnalysis().getLegalArea() : null;
            if (isOutOfScope(identifiedLegalArea)) {
                log.warn("Gemini identified out-of-scope legal area: sessionId={}, legalArea={}",
                        request.getSessionId(), identifiedLegalArea);
                meterRegistry.counter("ai.analysis.out_of_scope").increment();
                return buildOutOfScopeResponse(request, response, geminiDuration);
            }

            // 6. Enriquecer a resposta com metadados
            AiAnalysisResponse enriched = enrichResponse(
                    response, request, toolsUsed, jurisprudence, geminiDuration, start);

            meterRegistry.counter("ai.analysis.completed").increment();
            log.info("AI analysis completed: sessionId={}, caseId={}, duration={}ms, tools={}",
                    request.getSessionId(), request.getCaseId(),
                    System.currentTimeMillis() - start.toEpochMilli(), toolsUsed);

            return enriched;

        } catch (OutOfScopeLegalAreaException e) {
            log.warn("Out of scope legal area: sessionId={}, legalArea={}",
                    request.getSessionId(), request.getLegalArea());
            meterRegistry.counter("ai.analysis.out_of_scope").increment();
            return publishFailure(request, ErrorCode.OUT_OF_SCOPE_LEGAL_AREA,
                    e.getMessage(), false, start);

        } catch (AiProviderException e) {
            log.error("AI analysis failed: sessionId={}, errorCode={}, retryable={}, message={}",
                    request.getSessionId(), e.getErrorCode(), e.isRetryable(), e.getMessage());
            meterRegistry.counter("ai.analysis.failed").increment();
            return publishFailure(request, e.getErrorCode(),
                    sanitize(e.getMessage()), e.isRetryable(), start);

        } catch (DocumentNotFoundException e) {
            log.warn("AI analysis failed because document was not found: sessionId={}, message={}",
                    request.getSessionId(), e.getMessage());
            meterRegistry.counter("ai.analysis.failed").increment();
            return publishFailure(request, ErrorCode.DOCUMENT_NOT_FOUND,
                    sanitize(e.getMessage()), false, start);

        } catch (Exception e) {
            log.error("Unexpected error in AI analysis: sessionId={}", request.getSessionId(), e);
            meterRegistry.counter("ai.analysis.failed").increment();
            return publishFailure(request, ErrorCode.GEMINI_HTTP_ERROR,
                    "Unexpected analysis error", true, start);
        }
    }

    // ── Métodos auxiliares privados ─────────────────────────────────────────────

    private void validateLegalArea(String legalArea) {
        if (legalArea == null) return; // pode ser nulo — o modelo fará a inferência
        String upper = legalArea.toUpperCase();
        if (!appProperties.getAllowedLegalAreas().contains(upper)) {
            throw new OutOfScopeLegalAreaException(legalArea);
        }
    }

    /**
     * Verifica a área jurídica identificada pelo próprio Gemini a partir dos documentos.
     * Qualquer valor fora da lista de áreas permitidas (ex.: "FORA_DE_ESCOPO", "CRIMINAL",
     * "PENAL") é tratado como fora do escopo de análise automatizada.
     */
    private boolean isOutOfScope(String identifiedLegalArea) {
        if (identifiedLegalArea == null) return false;
        return !appProperties.getAllowedLegalAreas().contains(identifiedLegalArea.toUpperCase());
    }

    private AiAnalysisResponse buildOutOfScopeResponse(AiAnalysisRequest request,
                                                         AiAnalysisResponse geminiResponse,
                                                         long geminiDurationMs) {
        AiAnalysisResponse.Analysis geminiAnalysis = geminiResponse.getAnalysis();

        return AiAnalysisResponse.builder()
                .correlationId(request.getCorrelationId())
                .sessionId(request.getSessionId())
                .caseId(request.getCaseId())
                .tenantId(request.getTenantId())
                .status(AnalysisStatus.OUT_OF_SCOPE)
                .analysis(AiAnalysisResponse.Analysis.builder()
                        .legalArea(geminiAnalysis.getLegalArea())
                        .caseSummary(geminiAnalysis.getCaseSummary())
                        .parties(List.of())
                        .claims(List.of())
                        .evidences(List.of())
                        .risks(List.of())
                        .missingInformation(List.of())
                        .priority(geminiAnalysis.getPriority())
                        .jurisprudence(List.of())
                        .warnings(geminiAnalysis.getWarnings())
                        .build())
                .metadata(AiAnalysisResponse.Metadata.builder()
                        .model("gemini")
                        .processedAt(Instant.now())
                        .documentsAnalyzed(request.getDocuments().size())
                        .toolsUsed(List.of("CASE_SUMMARY"))
                        .geminiCallDurationMs(geminiDurationMs)
                        .build())
                .build();
    }

    private List<LegalDocument> fetchDocuments(AiAnalysisRequest request) {
        List<LegalDocument> docs = new ArrayList<>();
        for (AiAnalysisRequest.DocumentRef ref : request.getDocuments()) {
            log.debug("Fetching document: documentId={}, key={}", ref.getDocumentId(), ref.getS3Key());

            String text = storageGateway.getText(request.getS3Bucket(), ref.getS3Key());

            if (text == null || text.isBlank()) {
                throw new AiProviderException(ErrorCode.EMPTY_DOCUMENT,
                        "Document is empty: " + ref.getDocumentId());
            }
            if (text.length() > appProperties.getMaxDocumentChars()) {
                log.warn("Document exceeds max chars, truncating: documentId={}, chars={}",
                        ref.getDocumentId(), text.length());
                text = text.substring(0, appProperties.getMaxDocumentChars())
                        + "\n[... conteúdo truncado por limite máximo de caracteres]";
            }

            docs.add(LegalDocument.builder()
                    .documentId(ref.getDocumentId())
                    .documentType(ref.getDocumentType())
                    .s3Key(ref.getS3Key())
                    .textContent(text)
                    .build());
        }
        return docs;
    }

    /**
     * Injeta o conteúdo de texto resolvido de volta nos documentos da requisição
     * (usando o campo s3Key como campo de transporte).
     * Isso mantém o prompt builder desacoplado da chamada de armazenamento.
     */
    private void injectTextContent(AiAnalysisRequest request, List<LegalDocument> documents) {
        documents.forEach(doc -> request.getDocuments().stream()
                .filter(ref -> ref.getDocumentId().equals(doc.getDocumentId()))
                .findFirst()
                .ifPresent(ref -> ref.setS3Key(doc.getTextContent())));
    }

    private List<JurisprudenceResult> searchJurisprudence(AiAnalysisRequest request) {
        try {
            JurisprudenceSearchRequest searchReq = new JurisprudenceSearchRequest(
                    request.getLegalArea(),
                    request.getCaseId(),
                    List.of(),
                    5
            );
            List<JurisprudenceResult> results = jurisprudenceGateway.search(searchReq);
            log.debug("Jurisprudence search returned {} results for sessionId={}",
                    results.size(), request.getSessionId());
            return results;
        } catch (Exception e) {
            log.warn("Jurisprudence search failed, continuing without it: {}", e.getMessage());
            return List.of();
        }
    }

    private AiAnalysisResponse callGemini(AiAnalysisRequest request,
                                           List<JurisprudenceResult> jurisprudence) {
        // Reconstrói a requisição com o prompt enriquecido contendo a jurisprudência
        AiAnalysisRequest enrichedRequest = AiAnalysisRequest.builder()
                .correlationId(request.getCorrelationId())
                .sessionId(request.getSessionId())
                .caseId(request.getCaseId())
                .tenantId(request.getTenantId())
                .legalArea(request.getLegalArea())
                .s3Bucket(request.getS3Bucket())
                .documents(request.getDocuments())
                .requestedTools(request.getRequestedTools())
                .jurisprudence(jurisprudence)
                .build();

        return aiModelGateway.analyzeCase(enrichedRequest);
    }

    private AiAnalysisResponse enrichResponse(AiAnalysisResponse base,
                                               AiAnalysisRequest request,
                                               List<String> toolsUsed,
                                               List<JurisprudenceResult> jurisprudence,
                                               long geminiDurationMs,
                                               Instant start) {
        // Injeta a jurisprudência na análise caso o modelo não a tenha incluído
        if (base.getAnalysis() != null && !jurisprudence.isEmpty()
                && (base.getAnalysis().getJurisprudence() == null
                    || base.getAnalysis().getJurisprudence().isEmpty())) {
            base.getAnalysis().setJurisprudence(jurisprudence);
        }

        base.setMetadata(AiAnalysisResponse.Metadata.builder()
                .model("gemini")
                .processedAt(Instant.now())
                .documentsAnalyzed(request.getDocuments().size())
                .toolsUsed(toolsUsed)
                .geminiCallDurationMs(geminiDurationMs)
                .build());

        return base;
    }

    private AiAnalysisResponse publishFailure(AiAnalysisRequest request, ErrorCode code,
                                               String message, boolean retryable, Instant start) {
        return AiAnalysisResponse.builder()
                .correlationId(request.getCorrelationId())
                .sessionId(request.getSessionId())
                .caseId(request.getCaseId())
                .tenantId(request.getTenantId())
                .status(AnalysisStatus.FAILED)
                .error(AiAnalysisResponse.AnalysisError.builder()
                        .code(code.name())
                        .message(message)
                        .retryable(retryable)
                        .build())
                .metadata(AiAnalysisResponse.Metadata.builder()
                        .processedAt(Instant.now())
                        .build())
                .build();
    }

    /** Remove stack traces e detalhes internos das mensagens de erro antes de retornar */
    private String sanitize(String message) {
        if (message == null) return "Analysis error";
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }
}
