# Inventário de Regras e Riscos

## 1. Como ler este documento

Este inventário liga cada regra de negócio (RN) e requisito funcional (RF) a um comportamento esperado, ao risco de falha e à técnica de teste adequada. Ele serve de base para `caixa-preta.md`, `caixa-branca.md`, `caixa-cinza.md` e `rtm.md`.

- **Fontes:** `docs/prd/requisitos-funcionais.md`, `docs/openapi.yaml`, `docs/api-reservas.md` e o código em `src/main/java`.
- **Estado atual:** Implementado, Parcial ou Pendente, sempre com a classe e o método que comprovam. Pendente não é tratado como se funcionasse.
- **Impacto:** Alto, Médio ou Baixo, pelo dano se a regra falhar.
- **Técnica:** P = caixa preta, B = caixa branca, C = caixa cinza.
- **Crítico:** os requisitos RF07 a RF11 são críticos para a avaliação e estão marcados.
- **Decisão pendente:** pontos que dependem de decisão do grupo registrada em `estrategia.md`.

## 2. Inventário

Ordenado por impacto.

| ID | Regra e requisito | Comportamento esperado | Risco se falhar | Impacto | Técnica | Estado atual e evidência |
|---|---|---|---|---|---|---|
| INV-01 | RN01 / RF07 (crítico). Término posterior ao início. | Início ou fim ausente, ou fim igual ou anterior ao início, é recusado com 400. Vale ao solicitar e ao alterar o horário. | Reserva inválida gravada; conflito de horário calculado sobre intervalo sem sentido. | Alto | P, B | Implementado. `ReservaService.validarIntervalo` (IllegalArgumentException, 400). Os DTOs também exigem `@NotNull`, e `Reserva.isIntervaloValido` repete a regra. |
| INV-02 | RN02 / RF08 (crítico). Sem sobreposição na mesma sala. | Reserva que se sobrepõe a outra da mesma sala é recusada com 409. Intervalos adjacentes (o fim de um igual ao início do outro) são aceitos. Ao alterar o horário, a própria reserva não conta como conflito. | Dupla ocupação da sala, ou recusa indevida de reservas legítimas. | Alto | P, B, C | Implementado só para sala. `ReservaRepository.contarConflitos` (início anterior ao fim novo e fim posterior ao início novo) e `ReservaService.validarAusenciaDeConflito`. Ocupam a sala os status SOLICITADA, APROVADA e EM_USO. |
| INV-03 | RN03 / RF09 (crítico). Simultaneidade. | Duas solicitações simultâneas para a mesma sala e intervalo resultam em no máximo uma reserva aceita; a outra recebe 409. | Dupla reserva (falha que limita a nota). | Alto | C | Coberto por testes de integração concorrente em `ReservaPostgresIntegrationTest`: intervalos conflitantes aceitam uma solicitação e rejeitam a outra; intervalos não conflitantes são aceitos. O lock pessimista em `SalaRepository.buscarPorIdComBloqueio` é exercitado com PostgreSQL 16 via Testcontainers. |
| INV-04 | RN05 / RF10 (crítico). Aprovação de recurso restrito. | Somente o Responsável aprova recurso restrito; o recurso não restrito segue o fluxo normal. **Decisão pendente:** quem aprova e como o recurso é classificado como restrito. | Aprovação por perfil indevido. | Alto | P, B | Parcial. `ReservaService.aprovarReserva` aceita RESPONSAVEL **ou** ADMINISTRADOR, e não existe classificação de recurso restrito. Possível defeito frente à RN05. |
| INV-05 | RN04 / RF11 (crítico). Sala indisponível. | Sala cujo estado não é DISPONIVEL não aceita nova reserva nem alteração de horário (409). | Reserva em sala em manutenção. | Alto | P, B | Parcial. `ReservaService.validarDisponibilidadeDaSala` olha só o estado atual da sala. |
| INV-06 | RN02 / RF08 (crítico). Sobreposição de professor e material. | Conflito de horário também é detectado para professor e material envolvidos. | Professor ou material alocado em dois lugares ao mesmo tempo. | Alto | P, B, C | Pendente. A entidade `Reserva` só se liga a `Sala` e `Solicitante`; professor e material não fazem parte do modelo. |
| INV-07 | RN04 / RF11 (crítico). Bloqueios e manutenção por período. | Um período de bloqueio ou manutenção impede reservas só naquele intervalo. **Decisão pendente:** o que fazer com reservas já existentes dentro de um bloqueio novo. | Reservas aceitas em período bloqueado. | Alto | P, C | Pendente. Não há agenda de bloqueios nem endpoint administrativo. |
| INV-08 | RF01 e RF02. Autenticação e autorização. | O sistema identifica o usuário e bloqueia operações não permitidas ao perfil. | Qualquer pessoa age em nome de outra (falha de autorização, que limita a nota). | Alto | P, C | Pendente. Os IDs e perfis vêm no corpo da requisição e nada os comprova. |
| INV-09 | RN06 / RF15. Transições de estado. | Aprovar e rejeitar só a partir de SOLICITADA. Alterar horário só em SOLICITADA. Cancelar só em SOLICITADA ou APROVADA. Transições inválidas dão 409. | Reserva em estado incoerente. | Alto | P, B | Parcial. `Reserva.validarSolicitada`, `ReservaService.alterarHorarioReserva` e `cancelarReserva`. O enum tem 7 estados, mas a API só alcança SOLICITADA, APROVADA, REJEITADA e CANCELADA. |
| INV-10 | RF06. Posse da reserva e perfil. | Só o solicitante dono altera ou cancela a reserva (403 para outro). O tipo informado ao solicitar deve ser igual ao cadastrado (403). Solicitante, sala e reserva inexistentes dão 404. | Usuário altera reserva de outro. | Médio | P, B | Parcial. `ReservaService.validarSolicitante` e a comparação de `tipoSolicitante` em `solicitarReserva`. Sem autenticação, os IDs podem ser forjados. |
| INV-11 | Contrato de erros HTTP. | 400 (argumento ou corpo inválido), 403 (acesso negado), 404 (não encontrado), 409 (conflito ou estado inválido), com corpo de mensagem de erro. | Cliente recebe código errado e trata mal o erro. | Médio | P, B | Implementado. `ApiExceptionHandler`. `IllegalStateException` cobre conflito de horário, sala indisponível e estado inválido, todos como 409. |
| INV-12 | RF06. Consulta de reservas. | Consulta por ID existente devolve a reserva; ID inexistente, 404. A listagem por solicitante vem ordenada por início, do mais recente ao mais antigo. | Dado errado ou vazado ao consultar. | Médio | P, C | Parcial. `ReservaService.buscarReserva` e `listarReservasDoSolicitante`. A consulta não verifica a propriedade. |
| INV-13 | RN07 / RF14. Auditoria e não exclusão. | Mudanças de estado geram registro de auditoria. Reservas iniciadas não podem ser apagadas. | Sem rastreabilidade das mudanças. | Médio | C | Pendente. Não existe auditoria. Também não há endpoint de exclusão (confirmado: o `ReservaController` não tem `@DeleteMapping`). |
| INV-14 | RF18. Contrato OpenAPI. | As respostas reais seguem o `openapi.yaml`, inclusive campos obrigatórios e exemplos de erro. **Decisão pendente:** declarar propriedades obrigatórias e exemplos mais específicos. | Documentação diverge do comportamento. | Médio | P | Parcial. Existe contrato só para as reservas. |
| INV-15 | RF13 / RN08. Retirada e devolução de materiais. | Retirada e devolução ficam registradas. | Perda de rastreio de equipamento. | Baixo | P, C | Pendente. |
| INV-16 | RF19. Notificação. | Notificação simulada ou integração externa ao aprovar, rejeitar ou cancelar. **Decisão pendente:** evento, payload e resultado observável. | Usuário não é avisado. | Baixo | P | Pendente. Não há cliente de notificação. |
| INV-17 | RF16 e RF17. Relatórios e interface responsiva. | Relatórios de utilização e interface responsiva com mensagens claras. | Usuário sem visibilidade nem usabilidade. | Baixo | P | Pendente. Existe interface estática (`index.html`, `app.js`) com teste de contrato em `tests/frontend-contract.test.js`. |
| INV-18 | RF03, RF04, RF05 e RF12. Cadastros, pesquisa e validação de alocação docente. | Administrador gerencia recursos e usuários; usuários pesquisam recursos; Responsável valida alocação docente. | Dados mestres só criáveis direto no banco. | Baixo | P | Pendente. Não há endpoints. |

## 3. Observações do código que viram casos de teste

1. **Ordem das validações em `solicitarReserva`:** intervalo (400), sala (404), disponibilidade da sala (409), solicitante (404), tipo do solicitante (403) e conflito (409). Com duas falhas ao mesmo tempo, vale a primeira. Os testes devem fixar essa ordem.
2. **Mesmo 409 para causas diferentes:** conflito de horário, sala indisponível e estado inválido usam `IllegalStateException`. Os testes precisam diferenciar pela mensagem de erro.
3. **A aprovação não revalida a sala.** `aprovarReserva` só checa o estado da reserva. Se a sala entrar em manutenção depois da solicitação, a reserva ainda pode ser aprovada. Isso se liga a INV-07.
4. **Redundância na validação do intervalo.** `validarIntervalo` e `Reserva.isIntervaloValido` repetem a regra. Isso afeta a cobertura de ramos no JaCoCo e vale ser anotado.
5. **Defeito candidato em `Solicitante`:** o campo `email` usa `@UniqueElements`, anotação para coleções, o que provavelmente impede salvar um solicitante. Já foi corrigido na Fase A, mas deve entrar no registro de defeitos.
6. **A interface não chama a API.** Não há chamadas HTTP (`fetch`, `XMLHttpRequest` ou `/api/`) em `src/main/resources/static`. A interface trabalha com dados locais. Por isso um E2E por navegador não tem integração para verificar hoje, e a decisão sobre E2E em navegador depende de integrar a interface à API.
7. **Identidade no corpo da requisição:** `solicitanteId` e `usuarioId` vêm no JSON. Os testes com IDs conhecidos não comprovam autorização (INV-08).

## 4. Decisões pendentes que afetam o inventário

| Decisão | Linhas afetadas |
|---|---|
| Quem aprova recurso restrito e como se classifica o recurso | INV-04 |
| Abrangência do conflito: professor e material | INV-06 |
| Regra para reservas já existentes dentro de um bloqueio | INV-05, INV-07 |
| Evento, payload e resultado observável da notificação | INV-16 |
| Campos obrigatórios e exemplos de erro no OpenAPI | INV-14 |

Enquanto estas decisões não forem registradas em `estrategia.md`, as linhas afetadas permanecem como pendentes e não podem ser marcadas como aprovadas.