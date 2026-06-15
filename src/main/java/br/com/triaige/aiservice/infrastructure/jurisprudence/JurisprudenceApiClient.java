package br.com.triaige.aiservice.infrastructure.jurisprudence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Cliente de baixo nível para a mock API de jurisprudência.
 * Cuida apenas do HTTP — sem lógica de negócio aqui.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JurisprudenceApiClient {

    private final WebClient jurisprudenceWebClient;
    private final JurisprudenceProperties props;

    public List<JurisprudenceApiDto> fetchAll() {
        return jurisprudenceWebClient.get()
                .uri(props.getPath())
                .retrieve()
                .bodyToFlux(JurisprudenceApiDto.class)
                .collectList()
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .block();
    }
}
