package br.com.triaige.aiservice.application.port.out;

import br.com.triaige.aiservice.domain.model.JurisprudenceResult;
import br.com.triaige.aiservice.domain.model.JurisprudenceSearchRequest;

import java.util.List;

public interface JurisprudenceGateway {
    List<JurisprudenceResult> search(JurisprudenceSearchRequest request);
}
