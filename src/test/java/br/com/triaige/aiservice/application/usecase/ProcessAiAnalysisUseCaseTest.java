package br.com.triaige.aiservice.application.usecase;

import br.com.triaige.aiservice.application.port.out.*;
import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.exception.DocumentNotFoundException;
import br.com.triaige.aiservice.domain.exception.OutOfScopeLegalAreaException;
import br.com.triaige.aiservice.domain.model.*;
import br.com.triaige.aiservice.infrastructure.config.AppProperties;
import br.com.triaige.aiservice.infrastructure.gemini.GeminiPromptBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessAiAnalysisUseCase")
class ProcessAiAnalysisUseCaseTest {

    @Mock private DocumentStorageGateway storageGateway;
    @Mock private AiModelGateway aiModelGateway;
    @Mock private JurisprudenceGateway jurisprudenceGateway;
    @Mock private GeminiPromptBuilder promptBuilder;

    private MeterRegistry meterRegistry;
    private AppProperties appProperties;
    private ProcessAiAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        appProperties = new AppProperties();
        appProperties.setAllowedLegalAreas(List.of("CIVIL", "TRABALHISTA"));
        appProperties.setMaxDocumentChars(120_000);

        useCase = new ProcessAiAnalysisUseCase(
                storageGateway, aiModelGateway, jurisprudenceGateway,
                promptBuilder, appProperties, meterRegistry);
    }

    @Test
    @DisplayName("deve processar análise com sucesso")
    void shouldProcessAnalysisSuccessfully() {
        // Arrange
        AiAnalysisRequest request = buildRequest("TRABALHISTA", List.of("CASE_SUMMARY"));

        when(storageGateway.getText(anyString(), anyString())).thenReturn("Texto jurídico normalizado");
        when(aiModelGateway.analyzeCase(any())).thenReturn(buildSuccessResponse(request));

        // Act
        AiAnalysisResponse response = useCase.process(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("deve processar análise e retornar resultado do Gemini")
    void shouldCallGeminiAndReturnResult() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CIVIL", List.of("CASE_SUMMARY"));
        when(storageGateway.getText(anyString(), anyString())).thenReturn("Contrato de locação...");

        AiAnalysisResponse aiResponse = buildSuccessResponse(request);
        when(aiModelGateway.analyzeCase(any())).thenReturn(aiResponse);

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getSessionId()).isEqualTo("sess-001");
    }

    @Test
    @DisplayName("deve usar tool de jurisprudência quando solicitada")
    void shouldUseJurisprudenceToolWhenRequested() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CIVIL",
                List.of("CASE_SUMMARY", "JURISPRUDENCE_SEARCH"));
        when(storageGateway.getText(anyString(), anyString())).thenReturn("Texto do contrato");
        when(jurisprudenceGateway.search(any())).thenReturn(
                List.of(new JurisprudenceResult("T1", "TJSP", "0001",
                        "2024-01-01", "Resumo", "http://example.com")));
        when(aiModelGateway.analyzeCase(any())).thenReturn(buildSuccessResponse(request));

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        verify(jurisprudenceGateway).search(any());
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("deve marcar como FAILED quando área jurídica é criminal")
    void shouldFailForCriminalLegalArea() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CRIMINAL", List.of());

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.OUT_OF_SCOPE_LEGAL_AREA.name());
        assertThat(result.getError().isRetryable()).isFalse();
        verify(aiModelGateway, never()).analyzeCase(any());
    }

    @Test
    @DisplayName("deve marcar como FAILED quando documento está vazio")
    void shouldFailForEmptyDocument() {
        // Arrange
        AiAnalysisRequest request = buildRequest("TRABALHISTA", List.of());
        when(storageGateway.getText(anyString(), anyString())).thenReturn("");

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.EMPTY_DOCUMENT.name());
        verify(aiModelGateway, never()).analyzeCase(any());
    }

    @Test
    @DisplayName("deve marcar como FAILED DOCUMENT_NOT_FOUND quando documento não existe no S3")
    void shouldFailForMissingDocument() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CIVIL", List.of());
        when(storageGateway.getText(anyString(), anyString()))
                .thenThrow(new DocumentNotFoundException(
                        "triaige-processed", "tenant/sess/doc-001/normalized.txt"));

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND.name());
        assertThat(result.getError().getMessage()).contains("Document not found in S3");
        assertThat(result.getError().isRetryable()).isFalse();
        verify(aiModelGateway, never()).analyzeCase(any());
    }

    @Test
    @DisplayName("deve marcar como FAILED com retryable=true quando Gemini falha com timeout")
    void shouldFailWithRetryableOnGeminiTimeout() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CIVIL", List.of());
        when(storageGateway.getText(anyString(), anyString())).thenReturn("Conteúdo válido");
        when(aiModelGateway.analyzeCase(any())).thenThrow(
                new AiProviderException(ErrorCode.GEMINI_TIMEOUT, "Timeout"));

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.GEMINI_TIMEOUT.name());
        assertThat(result.getError().isRetryable()).isTrue();
    }

    @Test
    @DisplayName("deve continuar análise mesmo quando jurisprudência falha")
    void shouldContinueWhenJurisprudenceFails() {
        // Arrange
        AiAnalysisRequest request = buildRequest("CIVIL",
                List.of("CASE_SUMMARY", "JURISPRUDENCE_SEARCH"));
        when(storageGateway.getText(anyString(), anyString())).thenReturn("Texto válido");
        when(jurisprudenceGateway.search(any()))
                .thenThrow(new RuntimeException("Jurisprudence API unavailable"));
        when(aiModelGateway.analyzeCase(any())).thenReturn(buildSuccessResponse(request));

        // Act
        AiAnalysisResponse result = useCase.process(request);

        // Assert — deve ter sucesso mesmo com falha na jurisprudência
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private AiAnalysisRequest buildRequest(String legalArea, List<String> tools) {
        return AiAnalysisRequest.builder()
                .correlationId("corr-001")
                .sessionId("sess-001")
                .caseId("case-001")
                .tenantId("tenant-001")
                .legalArea(legalArea)
                .s3Bucket("triaige-processed")
                .documents(List.of(
                        AiAnalysisRequest.DocumentRef.builder()
                                .documentId("doc-001")
                                .documentType("PETICAO_INICIAL")
                                .s3Key("tenant/sess/doc-001/normalized.txt")
                                .build()))
                .requestedTools(tools)
                .build();
    }

    private AiAnalysisResponse buildSuccessResponse(AiAnalysisRequest request) {
        return AiAnalysisResponse.builder()
                .correlationId(request.getCorrelationId())
                .sessionId(request.getSessionId())
                .caseId(request.getCaseId())
                .tenantId(request.getTenantId())
                .status(AnalysisStatus.COMPLETED)
                .analysis(AiAnalysisResponse.Analysis.builder()
                        .legalArea(request.getLegalArea())
                        .caseSummary("Resumo do caso")
                        .parties(List.of())
                        .claims(List.of("Pedido de indenização"))
                        .risks(List.of())
                        .warnings(List.of("Análise automatizada."))
                        .build())
                .build();
    }
}
