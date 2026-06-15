package br.com.triaige.aiservice.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "triaige")
public class AppProperties {

    /** Número máximo de caracteres permitidos por texto de documento antes de rejeitar */
    private int maxDocumentChars = 120_000;

    /** Áreas jurídicas permitidas para análise automatizada */
    private List<String> allowedLegalAreas = List.of("CIVIL", "TRABALHISTA");
}
