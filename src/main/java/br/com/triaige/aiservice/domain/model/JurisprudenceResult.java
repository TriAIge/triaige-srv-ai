package br.com.triaige.aiservice.domain.model;

public record JurisprudenceResult(
        String title,
        String court,
        String processNumber,
        String judgmentDate,
        String summary,
        String sourceUrl
) {}
