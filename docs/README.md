# Documentação de API — triaige-srv-ai

Esta pasta contém os artefatos para explorar e testar localmente a API HTTP
do `triaige-srv-ai`.

## Arquivos

- **`openapi.yaml`** — Especificação OpenAPI 3.0 dos endpoints `/health`,
  `/internal/api/v1/analysis` e `/actuator/*`, com schemas de request/response e exemplos.
- **`postman_collection.json`** — Coleção do Postman com requisições prontas
  para os mesmos endpoints (incluindo exemplos de payload para `/internal/api/v1/analysis`).
- **`postman_environment.json`** — Environment do Postman com a variável
  `baseUrl` apontando para `http://localhost:8082`.

## Como usar

### Visualizar o OpenAPI

Abra `openapi.yaml` em qualquer visualizador (Swagger Editor, Redocly,
extensão do VS Code, etc.) ou importe-o no Postman/Insomnia.

### Importar no Postman

1. Importe `postman_collection.json` (File → Import).
2. Importe `postman_environment.json` e selecione o environment
   "TriAIge AI Service - Local".
3. Suba a aplicação localmente (`docker compose --profile app up` ou
   `mvn spring-boot:run` com o profile `local`).
4. Execute as requisições da pasta **Health** e **Internal**.

> O endpoint `POST /internal/api/v1/analysis` requer uma `GEMINI_API_KEY`
> válida para retornar uma análise real (status `COMPLETED`).

### Importar no Insomnia

O Insomnia também consegue importar `openapi.yaml` diretamente
(Create → Import From File).
