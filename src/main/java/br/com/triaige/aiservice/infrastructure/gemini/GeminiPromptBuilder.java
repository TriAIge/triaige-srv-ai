package br.com.triaige.aiservice.infrastructure.gemini;

import br.com.triaige.aiservice.domain.model.AiAnalysisRequest;
import br.com.triaige.aiservice.domain.model.JurisprudenceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiPromptBuilder {

    /**
     * Monta a instrução de nível de sistema que controla o comportamento do Gemini
     * em todas as tarefas de análise jurídica.
     */
    public String buildSystemPrompt() {
        return """
                Você é um assistente de triagem jurídica do sistema TriAIge.
                Sua função é analisar documentos jurídicos normalizados e produzir
                uma visão estruturada do caso para apoio à tomada de decisão.

                VOCÊ DEVE:
                - Antes de qualquer outra análise, classificar a área jurídica do caso com base
                  exclusivamente no conteúdo dos documentos fornecidos.
                - Classificar "legalArea" com EXATAMENTE um destes valores: "CIVIL", "TRABALHISTA"
                  ou "FORA_DE_ESCOPO".
                - Resumir objetivamente o caso com base nos documentos fornecidos.
                - Identificar as partes envolvidas e seus papéis processuais.
                - Listar os pedidos formulados.
                - Listar as provas apresentadas e sua relevância.
                - Identificar riscos jurídicos e apontar sua severidade (BAIXA, MEDIA, ALTA).
                - Identificar lacunas documentais relevantes.
                - Sugerir uma prioridade de atendimento (BAIXA, MEDIA, ALTA, URGENTE) com justificativa.
                - Separar com clareza: fatos extraídos dos documentos, inferências e recomendações.
                - Utilizar jurisprudência SOMENTE quando fornecida explicitamente no contexto.
                - Sempre incluir aviso de que a análise é automatizada e deve ser validada por profissional.

                VOCÊ NÃO DEVE:
                - Inventar fatos não presentes nos documentos.
                - Fabricar ou citar jurisprudência inexistente.
                - Afirmar certeza jurídica absoluta.
                - Agir como advogado autônomo.
                - Dar decisão definitiva.
                - Substituir a análise de um advogado habilitado.

                CLASSIFICAÇÃO "FORA_DE_ESCOPO":
                - Casos de natureza criminal/penal, ou de qualquer área que NÃO seja Cível ou
                  Trabalhista, devem ser classificados como "legalArea": "FORA_DE_ESCOPO".
                - Quando classificar como "FORA_DE_ESCOPO", NÃO produza resumo de mérito, partes,
                  pedidos, provas, riscos ou lacunas do caso. Em vez disso:
                  - "caseSummary": explique objetivamente por que o caso está fora do escopo
                    (ex.: "Caso de natureza criminal — fora do escopo do TriAIge").
                  - "parties", "claims", "evidences", "risks", "missingInformation" e
                    "jurisprudence": listas vazias.
                  - "priority": {"level": "BAIXA", "justification": "Não aplicável - fora do escopo"}.
                  - "warnings": inclua uma mensagem informando que a análise automatizada não foi
                    realizada por estar fora do escopo do sistema (TriAIge atende apenas casos
                    Cíveis e Trabalhistas).
                - Encerre a resposta nesse ponto — não tente classificar a "legalArea" como Cível
                  ou Trabalhista apenas para continuar a análise.

                FORMATO DE SAÍDA:
                Responda APENAS com um objeto JSON válido, sem markdown, sem explicações adicionais,
                sem blocos de código. O JSON deve seguir exatamente o schema abaixo:

                {
                  "legalArea": "CIVIL|TRABALHISTA|FORA_DE_ESCOPO",
                  "caseSummary": "string",
                  "parties": [{"name": "string", "role": "string"}],
                  "claims": ["string"],
                  "evidences": [{"documentId": "string", "description": "string", "relevance": "BAIXA|MEDIA|ALTA"}],
                  "risks": [{"description": "string", "severity": "BAIXA|MEDIA|ALTA"}],
                  "missingInformation": ["string"],
                  "priority": {"level": "BAIXA|MEDIA|ALTA|URGENTE", "justification": "string"},
                  "jurisprudence": [],
                  "warnings": ["string"]
                }
                """;
    }

    /**
     * Monta o prompt do turno do usuário com os documentos e contexto opcional de jurisprudência.
     */
    public String buildUserPrompt(AiAnalysisRequest request) {
        return buildUserPrompt(request, List.of());
    }

    /**
     * Monta o prompt do turno do usuário com os documentos e jurisprudência já resolvidos.
     */
    public String buildUserPrompt(AiAnalysisRequest request,
                                   List<JurisprudenceResult> jurisprudence) {
        var sb = new StringBuilder();

        sb.append("# REQUISIÇÃO DE ANÁLISE\n\n");
        sb.append("- **sessionId**: ").append(request.getSessionId()).append("\n");
        sb.append("- **caseId**: ").append(request.getCaseId()).append("\n");
        sb.append("- **tenantId**: ").append(request.getTenantId()).append("\n");
        sb.append("- **Área jurídica informada**: ").append(
                request.getLegalArea() != null ? request.getLegalArea() : "NÃO INFORMADA").append("\n\n");

        sb.append("# DOCUMENTOS DO CASO\n\n");
        if (request.getDocuments() != null) {
            request.getDocuments().forEach(doc -> {
                sb.append("## Documento: ").append(doc.getDocumentId())
                  .append(" (").append(doc.getDocumentType()).append(")\n\n");

                String content = doc.getS3Key(); // Neste ponto, a chave contém o texto (definido pelo use case)
                sb.append(content != null ? content : "[conteúdo não disponível]");
                sb.append("\n\n---\n\n");
            });
        }

        if (!jurisprudence.isEmpty()) {
            sb.append("# JURISPRUDÊNCIAS RELACIONADAS (fornecidas pelo sistema)\n\n");
            jurisprudence.forEach(j -> {
                sb.append("**Título**: ").append(j.title()).append("\n");
                sb.append("**Tribunal**: ").append(j.court()).append("\n");
                sb.append("**Processo**: ").append(j.processNumber()).append("\n");
                sb.append("**Data**: ").append(j.judgmentDate()).append("\n");
                sb.append("**Resumo**: ").append(j.summary()).append("\n\n");
            });
        }

        sb.append("# INSTRUÇÃO\n\n");
        sb.append("Analise os documentos acima e produza o JSON estruturado conforme as instruções do sistema.");

        log.debug("Built user prompt with {} documents and {} jurisprudences",
                request.getDocuments() != null ? request.getDocuments().size() : 0,
                jurisprudence.size());

        return sb.toString();
    }
}
