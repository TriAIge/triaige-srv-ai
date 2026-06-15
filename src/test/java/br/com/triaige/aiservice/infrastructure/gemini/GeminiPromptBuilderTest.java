package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.JurisprudenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GeminiPromptBuilder")
class GeminiPromptBuilderTest {

    private GeminiPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new GeminiPromptBuilder();
    }

    @Test
    @DisplayName("system prompt deve conter instruções de comportamento")
    void systemPromptShouldContainBehaviorInstructions() {
        String prompt = builder.buildSystemPrompt();

        assertThat(prompt).contains("TriAIge");
        assertThat(prompt).contains("JSON");
        assertThat(prompt).contains("criminal");
        assertThat(prompt).contains("jurisprudência");
        assertThat(prompt).containsIgnoringCase("inventar");
    }

    @Test
    @DisplayName("user prompt deve incluir todos os documentos")
    void userPromptShouldIncludeAllDocuments() {
        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .correlationId("c1")
                .sessionId("s1")
                .caseId("case-1")
                .tenantId("t1")
                .legalArea("CIVIL")
                .s3Bucket("bucket")
                .documents(List.of(
                        AiAnalysisRequest.DocumentRef.builder()
                                .documentId("doc-1")
                                .documentType("PETICAO_INICIAL")
                                .s3Key("Conteúdo da petição inicial aqui")
                                .build(),
                        AiAnalysisRequest.DocumentRef.builder()
                                .documentId("doc-2")
                                .documentType("CONTRATO")
                                .s3Key("Conteúdo do contrato aqui")
                                .build()))
                .requestedTools(List.of())
                .build();

        String prompt = builder.buildUserPrompt(request);

        assertThat(prompt).contains("doc-1");
        assertThat(prompt).contains("PETICAO_INICIAL");
        assertThat(prompt).contains("doc-2");
        assertThat(prompt).contains("CONTRATO");
        assertThat(prompt).contains("Conteúdo da petição");
        assertThat(prompt).contains("Conteúdo do contrato");
    }

    @Test
    @DisplayName("user prompt deve incluir jurisprudências quando fornecidas")
    void userPromptShouldIncludeJurisprudence() {
        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .correlationId("c1").sessionId("s1").caseId("case-1")
                .tenantId("t1").legalArea("CIVIL").s3Bucket("b")
                .documents(List.of()).requestedTools(List.of())
                .build();

        List<JurisprudenceResult> jurisprudence = List.of(
                new JurisprudenceResult(
                        "Decisão sobre responsabilidade civil", "TJSP",
                        "0001-22.0.00", "2024-03-15",
                        "Resumo da decisão", "https://example.com"));

        String prompt = builder.buildUserPrompt(request, jurisprudence);

        assertThat(prompt).contains("JURISPRUDÊNCIAS");
        assertThat(prompt).contains("Decisão sobre responsabilidade civil");
        assertThat(prompt).contains("TJSP");
        assertThat(prompt).contains("2024-03-15");
    }

    @Test
    @DisplayName("user prompt sem jurisprudência não deve mencionar seção de jurisprudências")
    void userPromptWithoutJurisprudenceShouldNotHaveSection() {
        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .correlationId("c1").sessionId("s1").caseId("case-1")
                .tenantId("t1").legalArea("TRABALHISTA").s3Bucket("b")
                .documents(List.of()).requestedTools(List.of())
                .build();

        String prompt = builder.buildUserPrompt(request, List.of());

        assertThat(prompt).doesNotContain("JURISPRUDÊNCIAS RELACIONADAS");
    }
}
