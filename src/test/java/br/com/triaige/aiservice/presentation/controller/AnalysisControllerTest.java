package br.com.triaige.aiservice.presentation.controller;

import br.com.triaige.aiservice.application.port.in.ProcessAiAnalysisPort;
import br.com.triaige.aiservice.domain.model.AiAnalysisResponse;
import br.com.triaige.aiservice.domain.model.AnalysisStatus;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import br.com.triaige.aiservice.presentation.dto.AnalysisRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisControllerTest {

    private final ProcessAiAnalysisPort processAiAnalysisPort = mock(ProcessAiAnalysisPort.class);
    private final AnalysisController controller = new AnalysisController(processAiAnalysisPort);

    @Test
    @DisplayName("deve retornar 200 quando analise completa com sucesso")
    void shouldReturnOkForCompletedAnalysis() {
        when(processAiAnalysisPort.process(any())).thenReturn(AiAnalysisResponse.builder()
                .status(AnalysisStatus.COMPLETED)
                .build());

        ResponseEntity<AiAnalysisResponse> response = controller.analyze(buildRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("deve retornar 400 quando o dominio falha por erro de entrada")
    void shouldReturnBadRequestForInputFailures() {
        when(processAiAnalysisPort.process(any())).thenReturn(failure(ErrorCode.DOCUMENT_NOT_FOUND));

        ResponseEntity<AiAnalysisResponse> response = controller.analyze(buildRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("deve retornar 502 quando o dominio falha por erro do Gemini")
    void shouldReturnBadGatewayForGeminiFailures() {
        when(processAiAnalysisPort.process(any())).thenReturn(failure(ErrorCode.GEMINI_HTTP_ERROR));

        ResponseEntity<AiAnalysisResponse> response = controller.analyze(buildRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("deve retornar 504 quando o Gemini expira")
    void shouldReturnGatewayTimeoutForGeminiTimeout() {
        when(processAiAnalysisPort.process(any())).thenReturn(failure(ErrorCode.GEMINI_TIMEOUT));

        ResponseEntity<AiAnalysisResponse> response = controller.analyze(buildRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("deve retornar 422 quando analise fica fora do escopo")
    void shouldReturnUnprocessableForOutOfScope() {
        when(processAiAnalysisPort.process(any())).thenReturn(AiAnalysisResponse.builder()
                .status(AnalysisStatus.OUT_OF_SCOPE)
                .build());

        ResponseEntity<AiAnalysisResponse> response = controller.analyze(buildRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private AiAnalysisResponse failure(ErrorCode code) {
        return AiAnalysisResponse.builder()
                .status(AnalysisStatus.FAILED)
                .error(AiAnalysisResponse.AnalysisError.builder()
                        .code(code.name())
                        .message("failure")
                        .retryable(code.isRetryable())
                        .build())
                .build();
    }

    private AnalysisRequest buildRequest() {
        AnalysisRequest.DocumentRef document = new AnalysisRequest.DocumentRef();
        document.setDocumentId("doc-001");
        document.setDocumentType("PETICAO_INICIAL");
        document.setS3Key("tenant-001/sess-2026-000001/doc-001/normalized.txt");

        AnalysisRequest request = new AnalysisRequest();
        request.setCorrelationId("corr-test-001");
        request.setSessionId("sess-2026-000001");
        request.setCaseId("CASO-2026-000001");
        request.setTenantId("tenant-001");
        request.setLegalArea("CIVIL");
        request.setS3Bucket("triaige-processed-documents");
        request.setDocuments(List.of(document));
        request.setRequestedTools(List.of("CASE_SUMMARY"));
        return request;
    }
}
