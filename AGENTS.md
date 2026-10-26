# Instrucoes do projeto

## Objetivo

Desenvolver e validar uma aplicacao para organizar reservas de recursos universitarios, incluindo salas, professores e materiais. O escopo e os requisitos vigentes estao em `docs/prd.md` e `docs/prd/`; nao presuma que itens ainda pendentes no PRD ja estejam implementados.

## Stack

- Java 21
- Spring Boot 3
- Maven (wrapper `./mvnw`)
- PostgreSQL 16 (`docker compose up -d` para o ambiente local; Testcontainers nos testes)

## Mapa do projeto

- `src/main/java/`: codigo Java da aplicacao, organizado em API, configuracao, entidades, enums, excecoes, repositorios e servicos.
- `src/main/resources/static/`: interface web estatica (HTML, CSS e JavaScript).
- `docs/`: PRD, requisitos, fluxos, historias, contrato OpenAPI, documentacao da API e documentos de teste (`docs/testes/`).
- `tests/`: verificacao do contrato do front-end (Node).

## Verificacao

O comando de verificacao e `./mvnw -B verify`. Testes de integracao exigem Docker.

## Convencoes

- Todo teste deve citar o requisito funcional (RF) ou a regra de negocio (RN) que verifica.
- Derive requisitos exclusivamente de `docs/prd.md` e dos documentos em `docs/prd/`. Nao invente requisitos nem transforme pendencias do PRD em comportamento definido.
- Preserve a rastreabilidade entre requisitos, implementacao e testes (`docs/rtm.md`).
- Cada caso de teste cita o ID dos documentos em `docs/testes/` (INV, CP, CB).

## Proibicoes

- Nao commitar diretamente na branch `main`.
- Nunca commitar segredos, credenciais ou valores sensiveis.
- Nao apagar nem desabilitar testes que falham para contornar uma falha; investigue e corrija a causa ou documente o bloqueio.