package br.com.triaige.aiservice.infrastructure.jurisprudence;

/**
 * Representa o formato de cada item retornado pela mock API de jurisprudência.
 */
public record JurisprudenceApiDto(
        String title,
        String court,
        String caseNumber,
        String judgmentDate,
        String summary,
        String url,
        String legalArea
) {}
