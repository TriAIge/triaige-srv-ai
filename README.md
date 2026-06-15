# triaige-srv-ai

Serviço de análise jurídica automatizada do projeto **TriAIge**.

Recebe, via chamada REST síncrona do `triaige-srv-orchestrator`, ponteiros para documentos normalizados no S3, chama o **Gemini** para produzir análise estruturada (com apoio opcional de busca de jurisprudência) e retorna o resultado na própria resposta HTTP.

---

## Sumário

- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Rodando localmente](#rodando-localmente)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Configuração externa (segredos)](#configuração-externa-segredos)
- [Fluxo de processamento](#fluxo-de-processamento)
- [Estrutura de pacotes](#estrutura-de-pacotes)

---

## Arquitetura

```
                POST /internal/api/v1/analysis
triaige-srv-orchestrator ────────────────────────►
                             ┌─────────────────────────────┐
                             │   triaige-srv-ai            │
                             │                             │
                             │   1. Buscar TXT no S3       │
                             │   2. Montar prompt jurídico │
                             │   3. Chamar Gemini          │
                             │   4. Buscar jurisprudência  │
                             │   5. Retornar resultado     │
                             └─────────────────────────────┘
                             ◄──────────────────────────────
                                  resposta HTTP síncrona
```

**Não faz:** OCR, upload de PDF, notificação, banco de dados, mensageria.

**Faz apenas:** receber requisição REST → buscar texto S3 → Gemini → retornar resultado.

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose
- LocalStack compartilhado do [`triaige-srv-orchestrator`](../triaige-srv-orchestrator)
- Chave de API Gemini (obtenha em https://aistudio.google.com)

---

## Rodando localmente

### 1. Configure a variável de ambiente

```bash
export GEMINI_API_KEY="sua-chave-gemini-aqui"
```

### 2. Suba a infraestrutura local compartilhada

```bash
cd ../triaige-srv-orchestrator
docker compose up localstack -d
```

Aguarde o health check:

```bash
docker compose ps
```

O LocalStack local fica centralizado no `triaige-srv-orchestrator` e cria:
- Buckets S3: `triaige-raw-documents`, `triaige-processed-documents`, `triaige-results`
- Filas SQS usadas pelo orquestrador
- Documento de teste em `s3://triaige-processed-documents/tenant-001/sess-2026-000001/doc-001/normalized.txt`

### 3. Rode o serviço

```bash
# Opção A — Maven com profile ho
SPRING_PROFILES_ACTIVE=ho \
S3_ENDPOINT=http://localhost:4566 \
GEMINI_API_KEY=$GEMINI_API_KEY \
mvn spring-boot:run

# Opção B — Docker Compose do app, usando o LocalStack compartilhado
docker-compose --profile app up --build
```

O serviço estará em `http://localhost:8082`.

Health check: `GET http://localhost:8082/health`

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `GEMINI_API_KEY` | _(obrigatório)_ | Chave da API Gemini |
| `GEMINI_MODEL` | `gemini-1.5-flash` | Modelo Gemini a usar |
| `GEMINI_TEMPERATURE` | `0.2` | Temperature (0.0–1.0) |
| `GEMINI_MAX_OUTPUT_TOKENS` | `8192` | Máximo de tokens na resposta |
| `GEMINI_TIMEOUT_SECONDS` | `60` | Timeout da chamada ao Gemini |
| `AWS_REGION` | `us-east-1` | Região AWS |
| `S3_ENDPOINT` | _(vazio = AWS real)_ | Endpoint S3 customizado (LocalStack) |
| `S3_FORCE_PATH_STYLE` | `false` | `true` para LocalStack |
| `TRIAIGE_MAX_DOCUMENT_CHARS` | `120000` | Limite de chars por documento |
| `LOG_LEVEL` | `INFO` | Nível de log |
| `SERVER_PORT` | `8082` | Porta HTTP |
| `CONFIG_REPO_PATH` | `../triaige-srv-ai-config/` | Caminho do repositório de [configuração externa](#configuração-externa-segredos) com os segredos do ambiente (opcional) |

---

## Configuração externa (segredos)

Valores sensíveis e específicos de cada ambiente (chave da API Gemini etc.)
**não ficam neste repositório**. Eles são carregados em tempo de execução a
partir do projeto irmão [`triaige-srv-ai-config`](../triaige-srv-ai-config), via:

```yaml
spring:
  config:
    import: "optional:file:${CONFIG_REPO_PATH:../triaige-srv-ai-config/}"
```

- O `triaige-srv-ai-config` contém **um arquivo por ambiente** (ex:
  `application-ho.yml`), seguindo a mesma convenção de profiles do Spring
  Boot.
- O import é **opcional** — sem o diretório/arquivo do profile ativo, os
  defaults de `application.yml`/`application-{profile}.yml` continuam
  valendo normalmente (ex: em `ho`, via variáveis de ambiente do
  `docker-compose.yml`).

Veja detalhes no [README do `triaige-srv-ai-config`].

---

## Fluxo de processamento

```
1. AnalysisController.analyze()   ← POST /internal/api/v1/analysis
   │
   ▼
2. ProcessAiAnalysisUseCase.process()
   │
   ├── Valida área jurídica (somente CIVIL / TRABALHISTA)
   │     → CRIMINAL ou desconhecida: retorna FAILED(OUT_OF_SCOPE)
   │
   ├── Busca textos normalizados no S3 via S3DocumentStorageGateway
   │     → não encontrado: retorna FAILED(DOCUMENT_NOT_FOUND)
   │     → vazio: retorna FAILED(EMPTY_DOCUMENT)
   │     → muito grande: trunca com aviso
   │
   ├── (opcional) Busca jurisprudência via JurisprudenceGateway
   │     → falha: loga e continua sem jurisprudência
   │
   ├── Monta prompt via GeminiPromptBuilder
   │
   ├── Chama Gemini via GeminiAiModelGateway
   │     → timeout: retorna FAILED(GEMINI_TIMEOUT, retryable=true)
   │     → erro HTTP: retorna FAILED(GEMINI_HTTP_ERROR, retryable=true)
   │     → JSON inválido: retorna FAILED(GEMINI_INVALID_RESPONSE)
   │
   └── Retorna AiAnalysisResponse (COMPLETED, OUT_OF_SCOPE ou FAILED) na resposta HTTP
```

---

## Estrutura de pacotes

```
br.com.triaige.aiservice
├── application
│   ├── port
│   │   ├── in
│   │   │   └── ProcessAiAnalysisPort.java     ← interface de entrada
│   │   └── out
│   │       ├── AiModelGateway.java            ← interface do modelo de IA
│   │       ├── DocumentStorageGateway.java    ← interface de armazenamento
│   │       └── JurisprudenceGateway.java      ← interface de jurisprudência
│   └── usecase
│       └── ProcessAiAnalysisUseCase.java      ← orquestra todo o fluxo
│
├── domain
│   ├── model
│   │   ├── AiAnalysisRequest.java
│   │   ├── AiAnalysisResponse.java
│   │   ├── AnalysisStatus.java
│   │   ├── ErrorCode.java
│   │   ├── JurisprudenceResult.java
│   │   ├── JurisprudenceSearchRequest.java
│   │   └── LegalDocument.java
│   └── exception
│       ├── AiProviderException.java
│       ├── DocumentNotFoundException.java
│       ├── InvalidAnalysisRequestException.java
│       └── OutOfScopeLegalAreaException.java
│
├── infrastructure
│   ├── aws
│   │   └── s3
│   │       └── S3DocumentStorageGateway.java
│   ├── config
│   │   ├── AppProperties.java
│   │   ├── AwsConfig.java
│   │   ├── AwsProperties.java
│   │   └── ObjectMapperConfig.java
│   ├── gemini
│   │   ├── GeminiAiModelGateway.java          ← implements AiModelGateway
│   │   ├── GeminiClient.java                  ← low-level HTTP
│   │   ├── GeminiDto.java                     ← wire request/response
│   │   ├── GeminiPromptBuilder.java
│   │   ├── GeminiProperties.java
│   │   └── GeminiWebClientConfig.java
│   └── jurisprudence
│       └── MockJurisprudenceGateway.java
│
└── presentation
    ├── controller
    │   ├── AnalysisController.java            ← POST /internal/api/v1/analysis
    │   └── HealthController.java
    └── dto
        └── AnalysisRequest.java               ← DTO de transporte da requisição REST
```
