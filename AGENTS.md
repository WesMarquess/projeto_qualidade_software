# Instrucoes do projeto

## Objetivo

Desenvolver e validar uma aplicacao para organizar reservas de recursos universitarios, incluindo salas, professores e materiais. O escopo e os requisitos vigentes estao em `docs/prd.md` e `docs/prd/`; nao presuma que itens ainda pendentes no PRD ja estejam implementados.

## Stack

- Java 21
- Spring Boot 3
- Maven

## Mapa do projeto

- `src/main/java/`: codigo Java da aplicacao, organizado em API, entidades, enums, excecoes, repositorios e servicos.
- `src/main/resources/static/`: interface web estatica (HTML, CSS e JavaScript).
- `docs/`: PRD, requisitos funcionais e nao funcionais, fluxos, historias, contrato OpenAPI e documentacao da API.
- `tests/`: testes e verificacoes do projeto.

## Verificacao

O comando alvo de verificacao e `./mvnw -B verify`. No estado atual, o wrapper `mvnw` e o `pom.xml` ainda nao existem na raiz; portanto, esse comando ainda nao pode ser executado neste repositorio.

## Convencoes

- Todo teste deve citar o requisito funcional (RF) ou a regra de negocio (RN) que verifica.
- Derive requisitos exclusivamente de `docs/prd.md` e dos documentos em `docs/prd/`. Nao invente requisitos nem transforme pendencias do PRD em comportamento definido.
- Preserve a rastreabilidade entre requisitos, implementacao e testes.

## Proibicoes

- Nao commitar diretamente na branch `main`.
- Nunca commitar segredos, credenciais ou valores sensiveis.
- Nao apagar nem desabilitar testes que falham para contornar uma falha; investigue e corrija a causa ou documente o bloqueio.
