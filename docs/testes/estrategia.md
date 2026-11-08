# Estratégia de Testes

## 1. Escopo

Esta estratégia orienta a verificação do backend atual de reservas e a evolução dos testes conforme os requisitos do PRD forem implementados. O escopo funcional atual é um recorte de reservas de salas; a matriz abaixo não considera requisito pendente como comportamento existente.

Estão no escopo de verificação da API descrita em `docs/openapi.yaml`:

- `POST /api/reservas`: solicitar reserva;
- `GET /api/reservas/{reservaId}` e `GET /api/reservas/solicitante/{solicitanteId}`: consultar reservas;
- `PUT /api/reservas/{reservaId}/horario`: alterar horário;
- `POST /api/reservas/{reservaId}/cancelamento`: cancelar;
- `POST /api/reservas/{reservaId}/aprovacao` e `POST /api/reservas/{reservaId}/rejeicao`: decidir solicitação.

Os cenários prioritários rastreiam RF07 a RF11 e as regras RN01 a RN05, conforme critério acadêmico. RF07 está implementado; RF08 a RF11 têm cobertura parcial ou lacunas descritas na matriz da seção 4. RF06 pode ser coberto nos fluxos implementados de solicitação, alteração e cancelamento.

Ficam fora da validação funcional da implementação atual: autenticação e autorização efetivas (RF01 e RF02), cadastros e pesquisa geral (RF03 a RF05), conflitos de professor e material, classificação de recursos restritos, manutenção por período, validação de alocação docente, movimentação de materiais, auditoria, ciclo completo de estados, relatórios e interface responsiva. Esses itens permanecem requisitos do PRD, mas não devem ser reportados como aprovados por testes de API que não os exercitam.

## 2. Níveis e ferramentas

| Nível | Ferramenta | Aplicação neste projeto |
|---|---|---|
| Unitário | JUnit 5, incluindo `@ParameterizedTest`, e Mockito | Verificar validação de intervalo e transições de `Reserva.aprovar` e `Reserva.rejeitar`; cobrir casos válidos, inválidos e limites sem iniciar a aplicação. Isolar repositórios com doubles de teste. |
| Integração | JUnit 5 e Testcontainers com PostgreSQL 16 | Exercitar serviço, repositórios, transações, consultas de conflito e locks usando o banco real da estratégia. Verificar `ReservaRepository.contarConflitos`, `buscarPorIdComBloqueio` e `SalaRepository.buscarPorIdComBloqueio`. |
| API caixa-preta | REST-assured | Enviar HTTP pelos endpoints publicados e validar códigos, cabeçalhos, corpos e erros definidos no OpenAPI, sem depender da estrutura interna das classes. |
| E2E | REST-assured, aplicação completa e PostgreSQL em Testcontainers | Executar jornadas pela API, da solicitação até consulta e, quando aplicável, alteração, cancelamento ou decisão, confirmando o estado persistido. Não há teste de navegador enquanto a interface web não chamar a API (hoje ela usa dados locais). |
| Concorrência | JUnit 5, REST-assured e Testcontainers com PostgreSQL | Disparar solicitações conflitantes em paralelo para a mesma sala e intervalo; conferir resultado final no banco e evidenciar que não houve dupla reserva (RF09 e RN03). |
| Segurança | JUnit 5 parametrizado, MockMvc ou REST-assured | Matriz perfil × operação (`docs/seguranca/matriz-autorizacao.md`), regras de posse, validação de entrada e respostas de erro sem stack trace, SQL ou nome de classe interna. Depende de RF01 e RF02. |
| Carga | Apache JMeter | Medir consultas, criação sem conflito e contenção por sala/intervalo; entregar plano e resultado. Metas experimentais provisórias: 50 usuários virtuais, 5 minutos, p95 abaixo de 500 ms e erros abaixo de 1%. Não são requisitos oficiais aprovados, precisam ser validadas e os resultados podem não atingi-las. |
| Integrações externas | WireMock, se aplicável | RF19 está pendente: a decisão entre notificação simulada e API externa, assim como eventos, payload e comportamento em falha ou timeout, ainda precisa ser definida. Não há cliente HTTP externo no código atual; WireMock será usado se uma integração for confirmada e implementada. |

Os testes de API devem cobrir os resultados documentados no OpenAPI, incluindo `201` na solicitação aceita e erros `400`, `403`, `404` ou `409` quando aplicáveis. O contrato deve declarar as propriedades obrigatórias da resposta `Reserva` e exemplos de erro para 400, 403, 404 e 409.

## 3. Caixa-preta, branca e cinza

**Caixa-preta:** usar o OpenAPI como contrato observável, sem inspecionar chamadas internas. REST-assured deve exercitar, por exemplo, `POST /api/reservas` com intervalo válido e inválido, `GET /api/reservas/{reservaId}` para recurso existente e inexistente e `PUT /api/reservas/{reservaId}/horario` para alteração permitida ou rejeitada. A resposta deve ser comparada ao contrato publicado. A ausência atual de autenticação não pode ser interpretada como autorização validada. Casos em `docs/testes/caixa-preta.md`.

**Caixa-branca:** orientar casos por decisões e ramos reais do código, com critério de condição nas expressões compostas. Em `ReservaService`, cobrir `validarIntervalo`, `validarDisponibilidadeDaSala`, `validarAusenciaDeConflito`, verificação de solicitante e os ramos de aprovador. Em `Reserva`, verificar a transição de estado e a rejeição de decisão quando o estado não é `SOLICITADA`. Em `ApiExceptionHandler`, verificar o mapeamento das exceções para HTTP. A cobertura estrutural é medida pelo JaCoCo; ela não substitui a evidência de requisito. Cada regra crítica passa por mutação manual: a regra é quebrada, o teste ligado a ela é executado e deve falhar. Casos em `docs/testes/caixa-branca.md`.

**Caixa-cinza:** chamar a aplicação pelo contrato, como um cliente externo, escolhendo os casos a partir do que existe por trás (locks, consultas e estados gravados) e verificando o resultado no banco. Usar REST-assured contra a aplicação conectada ao PostgreSQL do Testcontainers, sabendo que `SalaRepository.buscarPorIdComBloqueio` usa lock pessimista e que `ReservaRepository.contarConflitos` aplica a regra de sobreposição para salas. Esse nível é essencial para RF09 e RN03: um mock ou banco em memória não demonstra o comportamento do lock no PostgreSQL.

As classes de referência são [ReservaController](../../src/main/java/br/com/projeto_qualidade_software/projeto/api/ReservaController.java), [ReservaService](../../src/main/java/br/com/projeto_qualidade_software/projeto/services/ReservaService.java), [Reserva](../../src/main/java/br/com/projeto_qualidade_software/projeto/entities/Reserva.java), [ReservaRepository](../../src/main/java/br/com/projeto_qualidade_software/projeto/repositories/ReservaRepository.java), [SalaRepository](../../src/main/java/br/com/projeto_qualidade_software/projeto/repositories/SalaRepository.java) e [ApiExceptionHandler](../../src/main/java/br/com/projeto_qualidade_software/projeto/api/ApiExceptionHandler.java).

## 4. Critérios de saída e rastreabilidade crítica

Para a suíte de verificação, os critérios acadêmicos de saída são:

- cobertura mínima de 80% de linhas e 70% de branches no JaCoCo;
- zero defeito crítico em aberto na avaliação final, e zero bug ou vulnerabilidade crítica no SonarCloud;
- 100% dos requisitos críticos RF07 a RF11 registrados na RTM (`docs/rtm.md`), cada um ligado a cenários e evidência ou status de execução;
- plano e resultado de teste de carga entregues, sem declarar aprovado um limite de desempenho ainda não validado.

**Defeito crítico:** dupla reserva aceita, acesso indevido, perda ou corrupção de dados, aplicação que não sobe ou não reserva; no SonarCloud, vulnerabilidade ou bug de severidade blocker ou crítica.

A presença na RTM não significa que um requisito esteja implementado ou aprovado. Requisito bloqueado deve permanecer identificado como bloqueado ou pendente, sem evidência de teste passar em seu lugar.

| Requisito e regra | Cenário e evidência a rastrear | Situação conhecida no backend |
|---|---|---|
| RF07 e RN01 | Criar e alterar reserva com término posterior; rejeitar início ou fim ausentes ou término igual ou anterior ao início. Validar resposta HTTP e regra de intervalo. | Implementado em `ReservaService.validarIntervalo`; DTOs também validam campos obrigatórios. |
| RF08 e RN02 | Para a mesma sala, verificar intervalos sobrepostos e não sobrepostos e os estados que ocupam agenda. A detecção deve abranger também professor e material; registrar separadamente esses cenários quando forem suportados. | Parcial: `ReservaRepository.contarConflitos` considera apenas sala; professor e material não estão no modelo de reserva atual. |
| RF09 e RN03 | Submeter em paralelo duas solicitações para a mesma sala e intervalo contra PostgreSQL; exigir evidência de no máximo uma reserva aceita e nenhuma dupla reserva. | Parcial: há lock pessimista na sala durante o fluxo, mas falta teste de integração concorrente com o banco-alvo. |
| RF10 e RN05 | Somente o Responsável aprova recurso restrito; recurso não restrito segue o fluxo normal. O recurso terá um campo que o classifica como restrito. | Pendente ou parcial: não há classificação de recurso restrito; o código aceita `RESPONSAVEL` ou `ADMINISTRADOR` na decisão e não autentica o chamador. Não marcar como aprovado. |
| RF11 e RN04 | Tentar solicitar reserva para sala indisponível e verificar recusa. Separar indisponibilidade por estado de sala de bloqueio e manutenção por período. Um bloqueio novo é recusado enquanto existir reserva ativa no período. | Parcial: o serviço recusa sala cujo estado não seja `DISPONIVEL`; não há agenda de bloqueios ou manutenções por período. |

Os relatórios devem mostrar o resultado por requisito (passou, falhou, bloqueado ou não executado) e manter a ligação entre RF e RN, caso de teste e evidência.

## 5. Ambiente e dados de teste

- Runtime alvo: Java 21, Spring Boot 3 e Maven. O contrato OpenAPI usa `http://localhost:8080` como servidor local.
- O ambiente de integração usa PostgreSQL 16 descartável via Testcontainers, pois o teste de concorrência depende do comportamento de locks do banco. A CI usa `ubuntu-latest` do GitHub Actions, que já tem Docker.
- A base de teste deve ser isolada e reinicializada entre cenários ou suítes. Usar somente registros sintéticos, sem credenciais reais.
- Preparar salas nos estados `DISPONIVEL`, `RESERVADA` e `EM_MANUTENCAO`; solicitantes dos tipos `PROFESSOR` e `COORDENADOR`; aprovadores `RESPONSAVEL` e `ADMINISTRADOR` com IDs diferentes entre si; e reservas com estados e intervalos necessários aos cenários. Respeitar as restrições de persistência existentes, como capacidade mínima de 20 para `Sala`.
- Para conflitos, preparar intervalos coincidentes, sobrepostos e adjacentes; datas devem ser geradas de forma consistente durante a execução. O teste concorrente deve compartilhar a mesma sala e intervalo entre as requisições e consultar o resultado persistido.
- A responsividade da interface (RF17) é verificada manualmente em Chrome e Firefox na versão atual e em uma viewport de celular.
- O workflow `.github/workflows/ci.yml` executa `./mvnw -B verify` em pushes e pull requests. O runner hospedado `ubuntu-latest` disponibiliza Docker para os testes PostgreSQL com Testcontainers; a execução real do workflow ainda depende de um push e da validação no GitHub.

## 6. Riscos da estratégia

- A configuração da CI existe, mas sua execução no GitHub ainda precisa ser validada após um push; resultados locais não comprovam a execução no runner hospedado.
- RF08, RF10 e RF11 excedem o comportamento atualmente implementado; testar apenas o recorte existente pode criar falsa impressão de conformidade com o PRD.
- Um banco em memória ou mocks para repositórios não reproduzem necessariamente locks e concorrência do PostgreSQL; por isso, não servem como única evidência de RF09.
- Os identificadores e papéis enviados nas requisições não comprovam identidade. Testes com IDs conhecidos não validam autenticação nem autorização real.
- Não há cliente de notificação; o WireMock não terá integração concreta para verificar até RF19 ser implementado.
- As metas de carga são premissas ainda não medidas. Resultados do JMeter só viram aprovação depois de validadas.
- A interface web não chama a API, então não há E2E de navegador enquanto isso não mudar.
- `solicitadoEm` e `aprovadoEm` usam o relógio do sistema; asserts dependentes de horário exato podem ser instáveis.
- O prazo até a entrega é curto; o trabalho deve priorizar execução, pipeline, autorização e teste de dupla reserva.

## 7. Decisões

| Nº | Decisão | Motivo | Decidido por |
|---|---|---|---|
| 1 | PostgreSQL 16 em container (Testcontainers) nos testes; a CI usa `ubuntu-latest`, que já tem Docker. | A concorrência depende do comportamento de locks do banco real. | Grupo |
| 2 | `pom.xml`, `mvnw` e JaCoCo na fase de base. Exclusões do JaCoCo só para DTOs, classe principal, classes de configuração e `DevDataInitializer`, cada uma justificada. | Serviços, entidades com regra e o handler de erros devem ser medidos. | Grupo |
| 3 | RF10: o recurso terá um campo restrito. Somente `RESPONSAVEL` aprova recurso restrito (o código atual também aceita `ADMINISTRADOR`, possível defeito). Recurso não restrito passa direto para `APROVADA`. | RN05 da especificação. | Grupo |
| 4 | RF08: a detecção de sobreposição vale para sala, professor e material. | RN02 da especificação. Hoje só a sala é verificada (defeito a registrar). | Grupo |
| 5 | RF11: testar sala em manutenção, período futuro de bloqueio e reserva existente dentro de um bloqueio novo. O bloqueio novo é recusado enquanto existir reserva ativa no período. | Regra mais simples de testar e de explicar. | Grupo |
| 6 | RF19: decidir se haverá notificação simulada ou integração externa e definir eventos, contrato e comportamento em falha. Usar WireMock se uma integração HTTP for confirmada e implementada. | A decisão de escopo e o contrato ainda estão pendentes; não presumir uma integração inexistente. | Pendente |
| 7 | Carga (premissa a validar): 50 usuários, 5 minutos, p95 abaixo de 500 ms, erro abaixo de 1%, ambiente local em containers. | A disciplina não define a carga. | Grupo |
| 8 | E2E pela jornada completa da API com banco real. Teste de navegador só se a interface passar a chamar a API. | A interface atual não chama a API. | Grupo |
| 9 | Responsividade verificada manualmente em Chrome, Firefox e viewport de celular. Java 21 e PostgreSQL 16. | Escopo do E2E reduzido. | Grupo |
| 10 | Defeito crítico: dupla reserva aceita, acesso indevido, perda ou corrupção de dados, aplicação que não sobe ou não reserva; bug ou vulnerabilidade blocker ou crítica no SonarCloud. | Alinhado aos limitadores de nota. | Grupo |
| 11 | OpenAPI declara campos obrigatórios da resposta `Reserva` e exemplos de erro para 400, 403, 404 e 409, inclusive 403 em cancelamento e alteração de horário. | Hoje o contrato não declara o 403 desses endpoints. | Grupo |

## Documentos desta estratégia

- `docs/testes/inventario-regras.md`: regras, riscos e estado atual.
- `docs/testes/caixa-preta.md`: casos pelo contrato.
- `docs/testes/caixa-branca.md`: casos por decisões do código e prova de proteção.
- `docs/rtm.md`, `docs/defeitos/`, `docs/seguranca/matriz-autorizacao.md` e `docs/testes/mutacao-manual.md`: a produzir.

## Perguntas em aberto

- Qual mecanismo de autenticação será usado para RF01 e RF02 (token JWT ou sessão)?
- Qual funcionalidade nova será desenvolvida com TDD ou BDD (candidatas: aprovação de recurso restrito ou bloqueio por período)?
- O E2E pela API, sem navegador, atende ao critério de end-to-end da especificação? Confirmar com o professor.
- As metas de carga da decisão 7 se confirmam depois da primeira medição?