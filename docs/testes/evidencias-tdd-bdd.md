# Evidências de TDD e BDD

## Escopo e critério

Este documento distingue evidência de testes automatizados, especificação de comportamento e evidência histórica do processo de desenvolvimento. A existência de testes não demonstra, por si só, que TDD foi praticado.

Os requisitos acadêmicos registrados em [requisitos não funcionais](../prd/requisitos-nao-funcionais.md) listam JUnit 5, testes parametrizados, integração, API caixa-preta, E2E, Testcontainers, concorrência, CI, JaCoCo, SonarCloud e JMeter. Não estabelecem explicitamente TDD ou BDD como entregáveis obrigatórios. A [estratégia de testes](./estrategia.md) mantém em aberto qual funcionalidade será desenvolvida com TDD ou BDD.

## Evidências disponíveis

### Testes automatizados

O projeto contém testes unitários JUnit 5 em `ReservaServiceTest` e `ReservaTest`; `ReservaServiceTest` usa Mockito para isolar os repositórios. Também há testes de controller em `ReservaControllerTest` e testes com PostgreSQL 16/Testcontainers em `ReservaPostgresIntegrationTest`. Há testes parametrizados e cenários ligados a regras e requisitos funcionais. Esses artefatos demonstram cobertura automatizada atual, não a ordem histórica em que requisitos, testes e código foram criados.

Na execução registrada em 2026-10-26, `.\mvnw.cmd test` terminou com 130 testes aprovados, sem falhas, erros ou testes ignorados. Essa execução comprova o resultado da suíte naquele momento; não comprova um ciclo TDD.

### Especificação em estilo BDD

A história [1.1 — Front-end de reservas](../stories/1.1.front-end-reserva-de-salas.story.md) contém critérios Given/When/Then para um protótipo de interface. O arquivo [reservas.feature](./reservas.feature) registra cenários Gherkin para comportamentos de reserva já verificados pelos testes de integração e indica os métodos JUnit relacionados.

O arquivo `.feature` é uma especificação legível e rastreável. O projeto não configura Cucumber nem outro runner BDD; portanto, esses cenários não são executados automaticamente como Gherkin. Os testes JUnit correspondentes são executados separadamente.

## Limites da evidência histórica de TDD

O histórico Git consultado inclui commits de snapshot de sessões de agente, entre eles `1f949c1` (adição da classe de integração) e `5a8ea6e` (checkpoint com código e testes). Não foi identificada uma sequência inequívoca e rastreável que demonstre um teste falhando antes da implementação, o mesmo teste passando após a alteração de produção e uma etapa de refatoração. Não se conclui, portanto, que o processo histórico de TDD tenha ocorrido.

Não se devem criar commits, resultados de falha ou registros retroativos para preencher essa lacuna. Para obter evidência legítima, uma futura alteração de comportamento já definida deve registrar seu ciclo teste-falha, implementação, teste aprovado e eventual refatoração, com os commits e resultados reais. A estratégia cita aprovação de recurso restrito e bloqueio por período como candidatas, mas esses comportamentos dependem de decisões ainda pendentes e não são pressupostos neste registro.

## Rastreabilidade dos cenários Gherkin

| Cenário | Requisitos/regras | Teste JUnit relacionado |
|---|---|---|
| Duas solicitações conflitantes simultâneas para a mesma sala | RF09, RN03, INV-03 | `deveAceitarSomenteUmaReservaEmSolicitacoesConcorrentesComConflito` |
| Solicitações simultâneas em intervalos não sobrepostos | RF08, RF09, RN02, RN03, INV-02, INV-03 | `deveAceitarSolicitacoesConcorrentesParaIntervalosNaoSobrepostos` |
| Reservas em estados que ocupam a sala impedem sobreposição | RF08, RN02, INV-02 | `deveBloquearSomenteEstadosQueOcupamASala` |
| Reservas em estados que não ocupam a sala permitem a solicitação | RF08, RN02, INV-02 | `deveBloquearSomenteEstadosQueOcupamASala` |

Os testes ligados à concorrência usam PostgreSQL 16 via Testcontainers. A evidência se limita ao modelo atual, que verifica conflito de sala; não demonstra conflito de professor ou material, que ainda não integram o modelo de reserva.

## Situação

- **Testes automatizados:** presentes e executados; não são, isoladamente, prova de TDD.
- **Especificação BDD:** cenários Gherkin documentados e ligados a testes JUnit; não executáveis por runner BDD configurado.
- **Processo histórico TDD:** não comprovado pelo histórico consultado; permanece pendente de evidência contemporânea a uma futura mudança de comportamento definido.
