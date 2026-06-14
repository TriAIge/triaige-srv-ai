package br.com.triaige.aiservice.infrastructure.jurisprudence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jurisprudence")
public class JurisprudenceProperties {

    private String baseUrl = "https://6a2ecdd9c9776ca6c0c4f537.mockapi.io";
    private String path = "/jurisprudence-mock/jurisprudences";
    private int timeoutSeconds = 10;
}
