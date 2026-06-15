package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.AiAnalysisResponse;
import br.com.triaige.aiservice.domain.model.AnalysisStatus;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiAiModelGateway")
class GeminiAiModelGatewayTest {

    @Mock private GeminiClient geminiClient;

    private GeminiProperties props;
    private GeminiPromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private GeminiAiModelGateway gateway;

    @BeforeEach
    void setUp() {
        props = new GeminiProperties();
        props.setModel("gemini-2.5-flash");
        props.setTemperature(0.2);
        props.setMaxOutputTokens(8192);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        promptBuilder = new GeminiPromptBuilder();
        gateway = new GeminiAiModelGateway(geminiClient, props, promptBuilder, objectMapper);
    }

    @Test
    @DisplayName("deve parsear resposta JSON estruturada do Gemini com sucesso")
    void shouldParseStructuredJsonResponse() {
        // Arrange
        String geminiJson = """
                {
                  "legalArea": "CIVIL",
                  "caseSummary": "Caso sobre indenização por danos materiais.",
                  "parties": [{"name": "João Silva", "role": "AUTOR"}],
                  "claims": ["Indenização por danos materiais"],
                  "evidences": [],
                  "risks": [{"description": "Risco de perda", "severity": "MEDIA"}],
                  "missingInformation": [],
                  "priority": {"level": "MEDIA", "justification": "Documentação suficiente"},
                  "jurisprudence": [],
                  "warnings": ["Análise automatizada."]
                }
                """;

        GeminiDto.GenerateContentResponse geminiResponse = buildGeminiResponse(geminiJson);
        when(geminiClient.generateContent(any())).thenReturn(geminiResponse);

        AiAnalysisRequest request = buildRequest("CIVIL");

        // Act
        AiAnalysisResponse result = gateway.analyzeCase(request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getAnalysis().getLegalArea()).isEqualTo("CIVIL");
        assertThat(result.getAnalysis().getCaseSummary()).contains("indenização");
        assertThat(result.getAnalysis().getParties()).hasSize(1);
    }

    @Test
    @DisplayName("deve parsear resposta com markdown fences removidas")
    void shouldStripMarkdownFencesFromResponse() {
        // Arrange
        String geminiJson = """
                ```json
                {"legalArea":"TRABALHISTA","caseSummary":"Caso trabalhista","parties":[],"claims":[],"evidences":[],"risks":[],"missingInformation":[],"priority":{"level":"BAIXA","justification":"Simples"},"jurisprudence":[],"warnings":[]}
                ```
                """;

        when(geminiClient.generateContent(any())).thenReturn(buildGeminiResponse(geminiJson));

        // Act
        AiAnalysisResponse result = gateway.analyzeCase(buildRequest("TRABALHISTA"));

        // Assert
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getAnalysis().getLegalArea()).isEqualTo("TRABALHISTA");
    }

    @Test
    @DisplayName("deve lançar AiProviderException quando Gemini retorna candidatos vazios")
    void shouldThrowOnEmptyCandidates() {
        // Arrange
        GeminiDto.GenerateContentResponse emptyResponse = new GeminiDto.GenerateContentResponse();
        emptyResponse.setCandidates(List.of());
        when(geminiClient.generateContent(any())).thenReturn(emptyResponse);

        // Act & Assert
        assertThatThrownBy(() -> gateway.analyzeCase(buildRequest("CIVIL")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.GEMINI_INVALID_RESPONSE));
    }

    @Test
    @DisplayName("deve lançar AiProviderException quando JSON é inválido")
    void shouldThrowOnInvalidJson() {
        // Arrange
        when(geminiClient.generateContent(any()))
                .thenReturn(buildGeminiResponse("not-a-json-at-all"));

        // Act & Assert
        assertThatThrownBy(() -> gateway.analyzeCase(buildRequest("CIVIL")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.GEMINI_INVALID_RESPONSE));
    }

    @Test
    @DisplayName("deve propagar AiProviderException do GeminiClient")
    void shouldPropagateClientExceptions() {
        // Arrange
        when(geminiClient.generateContent(any()))
                .thenThrow(new AiProviderException(ErrorCode.GEMINI_TIMEOUT, "Timeout"));

        // Act & Assert
        assertThatThrownBy(() -> gateway.analyzeCase(buildRequest("CIVIL")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.GEMINI_TIMEOUT));
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private AiAnalysisRequest buildRequest(String legalArea) {
        return AiAnalysisRequest.builder()
                .correlationId("corr-001")
                .sessionId("sess-001")
                .caseId("case-001")
                .tenantId("tenant-001")
                .legalArea(legalArea)
                .s3Bucket("bucket")
                .documents(List.of(
                        AiAnalysisRequest.DocumentRef.builder()
                                .documentId("doc-001")
                                .documentType("PETICAO_INICIAL")
                                .s3Key("Texto jurídico completo do documento aqui")
                                .build()))
                .requestedTools(List.of())
                .build();
    }

    private GeminiDto.GenerateContentResponse buildGeminiResponse(String text) {
        GeminiDto.Part part = GeminiDto.Part.builder().text(text).build();
        GeminiDto.Content content = GeminiDto.Content.builder()
                .role("model").parts(List.of(part)).build();
        GeminiDto.Candidate candidate = new GeminiDto.Candidate();
        candidate.setContent(content);
        GeminiDto.GenerateContentResponse response = new GeminiDto.GenerateContentResponse();
        response.setCandidates(List.of(candidate));
        return response;
    }
}
