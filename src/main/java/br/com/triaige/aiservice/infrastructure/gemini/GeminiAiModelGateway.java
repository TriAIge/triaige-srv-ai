package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.application.port.out.AiModelGateway;
import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementa AiModelGateway usando a API REST do Gemini.
 * Monta a requisição, delega o HTTP para o GeminiClient e faz o parse da resposta JSON estruturada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiModelGateway implements AiModelGateway {

    private final GeminiClient geminiClient;
    private final GeminiProperties props;
    private final GeminiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public AiAnalysisResponse analyzeCase(AiAnalysisRequest request) {
        log.info("Sending case to Gemini: sessionId={}, caseId={}, model={}",
                request.getSessionId(), request.getCaseId(), props.getModel());

        List<JurisprudenceResult> jurisprudence = request.getJurisprudence() != null
                ? request.getJurisprudence() : List.of();

        String userPrompt = promptBuilder.buildUserPrompt(request, jurisprudence);
        String systemPrompt = promptBuilder.buildSystemPrompt();

        GeminiDto.GenerateContentRequest geminiRequest = GeminiDto.GenerateContentRequest.builder()
                .systemInstruction(GeminiDto.Content.builder()
                        .role("system")
                        .parts(List.of(GeminiDto.Part.builder().text(systemPrompt).build()))
                        .build())
                .contents(List.of(
                        GeminiDto.Content.builder()
                                .role("user")
                                .parts(List.of(GeminiDto.Part.builder().text(userPrompt).build()))
                                .build()))
                .generationConfig(GeminiDto.GenerationConfig.builder()
                        .temperature(props.getTemperature())
                        .maxOutputTokens(props.getMaxOutputTokens())
                        .responseMimeType("application/json")
                        .build())
                .build();

        GeminiDto.GenerateContentResponse geminiResponse =
                geminiClient.generateContent(geminiRequest);

        return parseResponse(geminiResponse, request);
    }

    private AiAnalysisResponse parseResponse(
            GeminiDto.GenerateContentResponse geminiResponse, AiAnalysisRequest request) {

        if (geminiResponse == null
                || geminiResponse.getCandidates() == null
                || geminiResponse.getCandidates().isEmpty()) {
            throw new AiProviderException(ErrorCode.GEMINI_INVALID_RESPONSE,
                    "Gemini returned empty candidates");
        }

        GeminiDto.Candidate candidate = geminiResponse.getCandidates().get(0);
        if (candidate.getContent() == null
                || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            throw new AiProviderException(ErrorCode.GEMINI_INVALID_RESPONSE,
                    "Gemini candidate has no content parts");
        }

        String rawText = candidate.getContent().getParts().get(0).getText();
        if (rawText == null || rawText.isBlank()) {
            throw new AiProviderException(ErrorCode.GEMINI_INVALID_RESPONSE,
                    "Gemini returned blank text");
        }

        // Remove os delimitadores de bloco de código markdown, se presentes
        String json = rawText.strip();
        if (json.startsWith("```json")) json = json.substring(7);
        else if (json.startsWith("```")) json = json.substring(3);
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        json = json.strip();

        try {
            AiAnalysisResponse.Analysis analysis = objectMapper.readValue(
                    json, AiAnalysisResponse.Analysis.class);

            return AiAnalysisResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .sessionId(request.getSessionId())
                    .caseId(request.getCaseId())
                    .tenantId(request.getTenantId())
                    .status(AnalysisStatus.COMPLETED)
                    .analysis(analysis)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON response: {}", rawText.substring(
                    0, Math.min(200, rawText.length())));
            throw new AiProviderException(ErrorCode.GEMINI_INVALID_RESPONSE,
                    "Could not parse Gemini response as JSON: " + e.getMessage(), e);
        }
    }
}
