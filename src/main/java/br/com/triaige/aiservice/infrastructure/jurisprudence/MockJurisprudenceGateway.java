package br.com.triaige.aiservice.infrastructure.jurisprudence;

import br.com.triaige.aiservice.application.port.out.JurisprudenceGateway;
import br.com.triaige.aiservice.domain.model.JurisprudenceResult;
import br.com.triaige.aiservice.domain.model.JurisprudenceSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementação de JurisprudenceGateway que consulta uma mock API externa
 * (https://6a2ecdd9c9776ca6c0c4f537.mockapi.io/jurisprudence-mock/jurisprudences)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockJurisprudenceGateway implements JurisprudenceGateway {

    private final JurisprudenceApiClient apiClient;

    @Override
    public List<JurisprudenceResult> search(JurisprudenceSearchRequest request) {
        log.info("MockJurisprudenceGateway.search: legalArea={}, keywords={}",
                request.legalArea(), request.keywords());

        if (request.legalArea() == null) {
            log.debug("No legalArea informed, skipping jurisprudence search");
            return List.of();
        }

        List<JurisprudenceApiDto> results;
        try {
            results = apiClient.fetchAll();
        } catch (Exception e) {
            log.warn("Failed to fetch jurisprudence from mock API: {}", e.getMessage());
            return List.of();
        }

        if (results == null) {
            return List.of();
        }

        return results.stream()
                .filter(j -> request.legalArea().equalsIgnoreCase(j.legalArea()))
                .limit(request.limit())
                .map(this::toResult)
                .toList();
    }

    private JurisprudenceResult toResult(JurisprudenceApiDto dto) {
        return new JurisprudenceResult(
                dto.title(),
                dto.court(),
                dto.caseNumber(),
                dto.judgmentDate(),
                dto.summary(),
                dto.url());
    }
}
