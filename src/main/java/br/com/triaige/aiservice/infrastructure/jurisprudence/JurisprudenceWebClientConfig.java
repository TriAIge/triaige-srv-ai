package br.com.triaige.aiservice.infrastructure.jurisprudence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class JurisprudenceWebClientConfig {

    private final JurisprudenceProperties props;

    @Bean
    public WebClient jurisprudenceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
