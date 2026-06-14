package br.com.triaige.aiservice.domain.model;

import java.util.List;

public record JurisprudenceSearchRequest(
        String legalArea,
        String summary,
        List<String> keywords,
        Integer limit
) {
    public JurisprudenceSearchRequest {
        if (limit == null) limit = 5;
    }
}
