package br.com.triaige.aiservice.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LegalDocument {

    private String documentId;
    private String documentType;
    private String s3Key;

    /** Conteúdo de texto normalizado obtido do S3 — nunca bytes brutos de PDF. */
    private String textContent;
}
