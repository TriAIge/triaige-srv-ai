package br.com.triaige.aiservice.infrastructure.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("deve desserializar content retornado pelo Gemini")
    void shouldDeserializeGeminiContent() throws Exception {
        String json = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "{\\"caseSummary\\":\\"ok\\"}" }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        GeminiDto.GenerateContentResponse response =
                objectMapper.readValue(json, GeminiDto.GenerateContentResponse.class);

        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().getFirst().getContent().getParts().getFirst().getText())
                .contains("caseSummary");
    }

    @Test
    @DisplayName("deve serializar responseMimeType como string")
    void shouldSerializeResponseMimeTypeAsString() throws Exception {
        GeminiDto.GenerateContentRequest request = GeminiDto.GenerateContentRequest.builder()
                .generationConfig(GeminiDto.GenerationConfig.builder()
                        .responseMimeType("application/json")
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"responseMimeType\":\"application/json\"");
    }
}
