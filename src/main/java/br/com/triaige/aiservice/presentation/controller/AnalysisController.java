package br.com.triaige.aiservice.presentation.controller;

import br.com.triaige.aiservice.application.port.in.ProcessAiAnalysisPort;
import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.AiAnalysisResponse;
import br.com.triaige.aiservice.domain.model.AnalysisStatus;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import br.com.triaige.aiservice.presentation.dto.AnalysisRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Endpoint chamado pelo triaige-srv-orchestrator para acionar, de forma síncrona,
 * a análise de IA (Gemini + jurisprudência) de uma sessão de triagem.
 */
@Slf4j
@RestController
@RequestMapping("/internal/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private final ProcessAiAnalysisPort processAiAnalysisPort;

    @PostMapping("/analysis")
    public Mono<ResponseEntity<AiAnalysisResponse>> analyze(
            @Valid @RequestBody AnalysisRequest analysisRequest) {

        log.info("POST /internal/api/v1/analysis - sessionId={}, caseId={}, correlationId={}",
                analysisRequest.getSessionId(), analysisRequest.getCaseId(), analysisRequest.getCorrelationId());

        List<AiAnalysisRequest.DocumentRef> docs = analysisRequest.getDocuments() == null
                ? List.of()
                : analysisRequest.getDocuments().stream()
                        .map(d -> AiAnalysisRequest.DocumentRef.builder()
                                .documentId(d.getDocumentId())
                                .documentType(d.getDocumentType())
                                .s3Key(d.getS3Key())
                                .build())
                        .toList();

        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .correlationId(analysisRequest.getCorrelationId())
                .sessionId(analysisRequest.getSessionId())
                .caseId(analysisRequest.getCaseId())
                .tenantId(analysisRequest.getTenantId())
                .legalArea(analysisRequest.getLegalArea())
                .s3Bucket(analysisRequest.getS3Bucket())
                .documents(docs)
                .requestedTools(analysisRequest.getRequestedTools() != null
                        ? analysisRequest.getRequestedTools() : List.of())
                .build();

        return Mono.fromCallable(() -> processAiAnalysisPort.process(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponseEntity);
    }

    private ResponseEntity<AiAnalysisResponse> toResponseEntity(AiAnalysisResponse response) {
        if (response.getStatus() == AnalysisStatus.FAILED) {
            return ResponseEntity.status(statusForFailure(response)).body(response);
        }
        if (response.getStatus() == AnalysisStatus.OUT_OF_SCOPE) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.ok(response);
    }

    private HttpStatus statusForFailure(AiAnalysisResponse response) {
        String code = response.getError() != null ? response.getError().getCode() : null;
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        try {
            return switch (ErrorCode.valueOf(code)) {
                case INVALID_MESSAGE, DOCUMENT_NOT_FOUND, EMPTY_DOCUMENT, OUT_OF_SCOPE_LEGAL_AREA ->
                        HttpStatus.BAD_REQUEST;
                case S3_ACCESS_DENIED, GEMINI_HTTP_ERROR, GEMINI_INVALID_RESPONSE,
                        JURISPRUDENCE_PROVIDER_ERROR -> HttpStatus.BAD_GATEWAY;
                case GEMINI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            };
        } catch (IllegalArgumentException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
