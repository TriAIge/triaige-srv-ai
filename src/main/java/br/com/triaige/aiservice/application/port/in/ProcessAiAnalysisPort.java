package br.com.triaige.aiservice.application.port.in;

import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.AiAnalysisResponse;

public interface ProcessAiAnalysisPort {
    AiAnalysisResponse process(AiAnalysisRequest request);
}
