package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Cliente de baixo nível para a API REST do Gemini.
 * Cuida do HTTP, das tentativas e do timeout — sem lógica de negócio aqui.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient geminiWebClient;
    private final GeminiProperties props;

    /**
     * Envia uma requisição generateContent ao Gemini e retorna a resposta bruta.
     * Tenta novamente em caso de 429/5xx até maxRetries vezes.
     */
    public GeminiDto.GenerateContentResponse generateContent(
            GeminiDto.GenerateContentRequest request) {

        String url = String.format(
                "/v1beta/models/%s:generateContent?key=%s",
                props.getModel(), props.getApiKey());

        log.debug("Calling Gemini model={}", props.getModel());

        try {
            return geminiWebClient.post()
                    .uri(url)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        if (response.statusCode().value() == 429) {
                            return response.createException()
                                    .map(e -> new AiProviderException(
                                            ErrorCode.GEMINI_HTTP_ERROR,
                                            "Gemini rate limit exceeded", e));
                        }
                        return response.createException()
                                .map(e -> new AiProviderException(
                                        ErrorCode.GEMINI_HTTP_ERROR,
                                        "Gemini client error: " + response.statusCode(), e, false));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.createException()
                                    .map(e -> new AiProviderException(
                                            ErrorCode.GEMINI_HTTP_ERROR,
                                            "Gemini server error: " + response.statusCode(), e)))
                    .bodyToMono(GeminiDto.GenerateContentResponse.class)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .retryWhen(Retry.backoff(props.getMaxRetries(),
                                    Duration.ofMillis(props.getRetryBackoffMs()))
                            .filter(this::isRetryable)
                            .doBeforeRetry(sig -> log.warn(
                                    "Retrying Gemini call, attempt {}: {}",
                                    sig.totalRetries() + 1, sig.failure().getMessage())))
                    .block();

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            // Quando os retries se esgotam, o Reactor embrulha o último erro real em
            // RetryExhaustedException com a mensagem genérica "Retries exhausted: N/N".
            // Desembrulhamos para propagar a causa de verdade.
            Throwable rootCause = Exceptions.isRetryExhausted(e) && e.getCause() != null
                    ? e.getCause() : e;

            if (rootCause instanceof AiProviderException ape) {
                throw ape;
            }

            String message = rootCause.getMessage() != null ? rootCause.getMessage() : e.getMessage();
            if (rootCause instanceof TimeoutException
                    || (message != null && message.toLowerCase().contains("timeout"))) {
                throw new AiProviderException(ErrorCode.GEMINI_TIMEOUT,
                        "Timeout calling Gemini after " + props.getTimeoutSeconds() + "s", rootCause);
            }
            throw new AiProviderException(ErrorCode.GEMINI_HTTP_ERROR,
                    "Unexpected error calling Gemini: " + message, rootCause);
        }
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof AiProviderException ape) {
            return ape.isRetryable();
        }
        return t instanceof java.net.ConnectException;
    }
}
