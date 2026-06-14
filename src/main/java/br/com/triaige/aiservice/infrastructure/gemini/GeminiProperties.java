package br.com.triaige.aiservice.infrastructure.gemini;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey;
    private String model = "gemini-2.5-flash";
    private double temperature = 0.2;
    private int maxOutputTokens = 8192;
    private int timeoutSeconds = 60;
    private String baseUrl = "https://generativelanguage.googleapis.com";
    /** Número máximo de tentativas em erros retentáveis do Gemini */
    private int maxRetries = 3;
    /** Backoff em ms entre as tentativas */
    private long retryBackoffMs = 2000;
}
