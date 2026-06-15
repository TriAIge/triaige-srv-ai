package br.com.triaige.aiservice.application.port.out;

import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.AiAnalysisResponse;

public interface AiModelGateway {
    AiAnalysisResponse analyzeCase(AiAnalysisRequest request);
}
