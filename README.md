# Organização de Recursos

Sistema de alocação de salas, professores e materiais sem conflitos de horário. Projeto semestral de Qualidade de Software (Senac TADS 2026.2), com Java 21, Spring Boot 3, Maven e PostgreSQL.

## Estado atual

A API de reservas de salas está implementada (solicitar, consultar, alterar horário, cancelar, aprovar e rejeitar). Os demais requisitos (autenticação, professores e materiais, bloqueios por período, auditoria, relatórios) estão pendentes. A situação de cada requisito está em [`docs/prd/requisitos-funcionais.md`](docs/prd/requisitos-funcionais.md), na seção "Situação da implementação atual".

## Pré-requisitos

- Java 21
- Docker (PostgreSQL local e testes com Testcontainers)
- Git

## Como executar

```
docker compose up -d
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`. A interface estática em `/` ainda usa dados locais e não chama a API.

Na primeira execução são criados dados fictícios: sala `1` (30 lugares), solicitante `1` (PROFESSOR), responsável `1` e administrador `1`. Para desligar, use `APP_SEED_ENABLED=false`.

Variáveis de ambiente opcionais: `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## Exemplo de uso da API

```
curl -i -X POST http://localhost:8080/api/reservas \
  -H "Content-Type: application/json" \
  -d '{"salaId":1,"solicitanteId":1,"tipoSolicitante":"PROFESSOR","inicio":"2026-11-02T10:00:00","fim":"2026-11-02T11:00:00"}'
```

Contrato completo em [`docs/openapi.yaml`](docs/openapi.yaml) e guia em [`docs/api-reservas.md`](docs/api-reservas.md).

## Como testar

```
./mvnw -B verify
```

A suíte automatizada está em construção. O planejamento dos testes está em `docs/testes/`.

## Documentação

- [PRD](docs/prd.md) e [requisitos funcionais](docs/prd/requisitos-funcionais.md)
- [Requisitos não funcionais](docs/prd/requisitos-nao-funcionais.md)
- [Fluxos de sequência](docs/prd/fluxos-sequencia.md) e [personas](docs/prd/personas/)
- [Estratégia de testes](docs/testes/estrategia.md)
- [Inventário de regras e riscos](docs/testes/inventario-regras.md)
- [Casos de caixa preta](docs/testes/caixa-preta.md) e [de caixa branca](docs/testes/caixa-branca.md)
- [Registro do uso de IA](docs/ia/log-prompts.md)

## Estrutura

```
.
├── pom.xml, mvnw, docker-compose.yml
├── src/main/java/br/com/projeto_qualidade_software/projeto/
│   ├── api/            controllers, DTOs e tratamento de erros
│   ├── config/         dados iniciais de desenvolvimento
│   ├── entities/       entidades JPA
│   ├── enums/          estados e tipos
│   ├── exceptions/     exceções de negócio
│   ├── repositories/   acesso a dados
│   └── services/       regras de negócio
├── src/main/resources/static/   interface web estática
├── docs/               requisitos, contrato da API e documentos de teste
└── tests/              verificação do contrato do front-end (Node)
```

## Como contribuir

Trabalhe em uma branch, nunca direto na `main`. Rode `./mvnw -B verify` antes de cada commit e abra pull request com revisão de outro integrante do grupo.