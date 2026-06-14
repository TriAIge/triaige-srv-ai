package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.domain.model.JurisprudenceResult;
import br.com.triaige.aiservice.domain.model.JurisprudenceSearchRequest;
import br.com.triaige.aiservice.infrastructure.jurisprudence.JurisprudenceApiClient;
import br.com.triaige.aiservice.infrastructure.jurisprudence.JurisprudenceApiDto;
import br.com.triaige.aiservice.infrastructure.jurisprudence.MockJurisprudenceGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockJurisprudenceGateway")
class MockJurisprudenceGatewayTest {

    @Mock private JurisprudenceApiClient apiClient;

    private MockJurisprudenceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockJurisprudenceGateway(apiClient);
    }

    @Test
    @DisplayName("deve retornar jurisprudências trabalhistas")
    void shouldReturnTrabalhistaResults() {
        when(apiClient.fetchAll()).thenReturn(sampleData());

        JurisprudenceSearchRequest req = new JurisprudenceSearchRequest(
                "TRABALHISTA", "Horas extras", List.of("horas", "extras"), 5);

        List<JurisprudenceResult> results = gateway.search(req);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.title()).isNotBlank();
            assertThat(r.court()).isNotBlank();
            assertThat(r.processNumber()).isNotBlank();
        });
    }

    @Test
    @DisplayName("deve retornar jurisprudências cíveis")
    void shouldReturnCivelResults() {
        when(apiClient.fetchAll()).thenReturn(sampleData());

        JurisprudenceSearchRequest req = new JurisprudenceSearchRequest(
                "CIVIL", "Indenização", List.of("danos", "materiais"), 5);

        List<JurisprudenceResult> results = gateway.search(req);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).court()).isEqualTo("TJSP");
    }

    @Test
    @DisplayName("deve retornar lista vazia para área não suportada")
    void shouldReturnEmptyForUnsupportedArea() {
        when(apiClient.fetchAll()).thenReturn(sampleData());

        JurisprudenceSearchRequest req = new JurisprudenceSearchRequest(
                "ADMINISTRATIVO", "Servidor público", List.of(), 5);

        List<JurisprudenceResult> results = gateway.search(req);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("deve respeitar o limite de resultados")
    void shouldRespectLimit() {
        when(apiClient.fetchAll()).thenReturn(sampleData());

        JurisprudenceSearchRequest req = new JurisprudenceSearchRequest(
                "TRABALHISTA", "Horas extras", List.of(), 1);

        List<JurisprudenceResult> results = gateway.search(req);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando a mock API falha")
    void shouldReturnEmptyWhenApiFails() {
        when(apiClient.fetchAll()).thenThrow(new RuntimeException("API unavailable"));

        JurisprudenceSearchRequest req = new JurisprudenceSearchRequest(
                "CIVIL", "Indenização", List.of(), 5);

        List<JurisprudenceResult> results = gateway.search(req);

        assertThat(results).isEmpty();
    }

    private List<JurisprudenceApiDto> sampleData() {
        return List.of(
                new JurisprudenceApiDto(
                        "Indenização por horas extras não pagas",
                        "TST",
                        "0001234-56.2023.5.00.0000",
                        "2023-08-15",
                        "Resumo trabalhista",
                        "https://jurisprudencia.tst.jus.br/mock/0001234",
                        "TRABALHISTA"),
                new JurisprudenceApiDto(
                        "Dano moral por assédio moral no ambiente de trabalho",
                        "TRT-2",
                        "0009876-54.2023.5.02.0000",
                        "2023-09-20",
                        "Resumo trabalhista 2",
                        "https://jurisprudencia.trt2.jus.br/mock/0009876",
                        "TRABALHISTA"),
                new JurisprudenceApiDto(
                        "Responsabilidade civil por danos materiais",
                        "TJSP",
                        "0002345-67.2023.8.26.0000",
                        "2023-07-12",
                        "Resumo cível",
                        "https://esaj.tjsp.jus.br/mock/0002345",
                        "CIVIL")
        );
    }
}
