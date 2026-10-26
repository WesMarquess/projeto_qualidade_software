# Casos de Teste de Caixa Preta

## 1. Escopo e premissas

Estes casos verificam a API de reservas **somente pelo contrato** (`docs/openapi.yaml` e `docs/api-reservas.md`) e pelas regras do `docs/testes/inventario-regras.md`. Nenhum resultado esperado depende da estrutura interna das classes.

- **Ferramenta prevista:** REST-assured contra a aplicação conectada ao PostgreSQL (Testcontainers). Os casos também podem ser executados manualmente com `curl`.
- **Técnicas:** valor limite (VL), partição de equivalência (PE), tabela de decisão (TD) e transição de estado (TR).
- **Identificação:** cada caso tem ID `CP-nn` e liga a uma linha do inventário (INV-nn). As transições têm IDs `TR-nn` e a tabela de decisão `TD-nn`.
- **Códigos HTTP:** 201, 200, 400, 403, 404 e 409, conforme o contrato. O corpo de erro tem o campo `mensagem`.
- **Resultado esperado** vem do contrato e das regras. Onde o contrato é omisso, o caso é marcado com **[verificar]**.
- **Decisão pendente:** casos que dependem de decisão do grupo registrada em `estrategia.md` levam esta marca.

## 2. Massa de teste

Dados sintéticos, recriados antes de cada caso ou suíte.

| Símbolo | Descrição |
|---|---|
| S1 | Sala com capacidade 30, estado DISPONIVEL |
| S2 | Sala em estado EM_MANUTENCAO |
| S3 | Sala em estado RESERVADA |
| S4 | Outra sala DISPONIVEL |
| P1, P2 | Solicitantes do tipo PROFESSOR |
| C1 | Solicitante do tipo COORDENADOR |
| R1 | Responsável cadastrado |
| A1 | Administrador cadastrado |
| D | Data futura base; D+1 é o dia seguinte |
| E | Reserva existente em S1, de P1, em D das 10:00 às 11:00, estado SOLICITADA (quando o caso diz outro estado, ele é informado) |

Os IDs de R1 e A1 devem ser **diferentes**, para detectar confusão entre os dois cadastros.

## 3. Intervalo da reserva (INV-01)

### 3.1 Valor limite ao solicitar

Requisição: POST `/api/reservas` com S1, P1, PROFESSOR, no dia D, sem reservas prévias.

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-01 | VL | `inicio` ausente | 400 |
| CP-02 | VL | `fim` ausente | 400 |
| CP-03 | VL | `inicio` 11:00 e `fim` 10:00 (fim antes do início) | 400 |
| CP-04 | VL | `inicio` 10:00 e `fim` 10:00 (iguais) | 400 |
| CP-05 | VL | `inicio` 10:00 e `fim` 10:01 (um minuto de duração) | 201, status SOLICITADA |
| CP-06 | PE | `inicio` 08:00 e `fim` 18:00 (intervalo longo) | 201 |
| CP-07 | PE | `inicio` D 23:00 e `fim` D+1 01:00 (atravessa a meia-noite) | 201 |
| CP-08 | PE | `inicio` e `fim` ausentes | 400 |

### 3.2 Alterar horário (PUT `/api/reservas/{id}/horario`)

Pré-condição: reserva SOLICITADA de P1 em S1.

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-09 | PE | Novo intervalo válido | 200, horário atualizado, status continua SOLICITADA |
| CP-10 | VL | `fim` igual ao `inicio` | 400 |
| CP-11 | VL | `fim` antes do `inicio` | 400 |
| CP-12 | PE | `inicio` ausente | 400 |

### 3.3 Campos obrigatórios e formato (POST `/api/reservas`)

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-13 | PE | Corpo ausente | 400 |
| CP-14 | PE | JSON malformado | 400 |
| CP-15 | PE | `salaId` ausente | 400 |
| CP-16 | VL | `salaId` igual a 0 | 400 |
| CP-17 | PE | `salaId` negativo | 400 |
| CP-18 | PE | `solicitanteId` ausente | 400 |
| CP-19 | VL | `solicitanteId` igual a 0 | 400 |
| CP-20 | PE | `tipoSolicitante` ausente | 400 |
| CP-21 | PE | `tipoSolicitante` fora do enum (por exemplo ALUNO) | 400 |
| CP-22 | PE | `inicio` em formato inválido (por exemplo texto livre) | 400 |
| CP-23 | PE | `salaId` não numérico (por exemplo abc) | 400 |

## 4. Sobreposição de horários (INV-02)

### 4.1 Partições de sobreposição na mesma sala

Pré-condição: E existe (S1, 10:00 às 11:00, SOLICITADA). Requisição: POST `/api/reservas` por P2 na sala indicada.

| ID | Técnica | Intervalo pedido | Resultado esperado |
|---|---|---|---|
| CP-24 | PE | S1, 10:00 às 11:00 (idêntico) | 409 |
| CP-25 | PE | S1, 10:15 às 10:45 (contido em E) | 409 |
| CP-26 | PE | S1, 09:00 às 12:00 (contém E) | 409 |
| CP-27 | PE | S1, 09:30 às 10:30 (sobrepõe o início) | 409 |
| CP-28 | PE | S1, 10:30 às 11:30 (sobrepõe o fim) | 409 |
| CP-29 | VL | S1, 11:00 às 12:00 (adjacente depois) | 201 |
| CP-30 | VL | S1, 09:00 às 10:00 (adjacente antes) | 201 |
| CP-31 | PE | S1, 13:00 às 14:00 (separado) | 201 |
| CP-32 | PE | S4, 10:00 às 11:00 (mesmo horário, outra sala) | 201 |
| CP-33 | PE | S1, 10:00 às 11:00 em D+1 (mesmo horário, outro dia) | 201 |
| CP-34 | VL | S1, 10:00 às 10:01 (um minuto dentro de E) | 409 |

### 4.2 Estados da reserva existente que ocupam a sala

Pré-condição: E no estado indicado. Requisição: P2 pede S1, 10:00 às 11:00.

| ID | Técnica | Estado de E | Resultado esperado |
|---|---|---|---|
| CP-35 | PE | APROVADA | 409 |
| CP-36 | PE | CANCELADA | 201 |
| CP-37 | PE | REJEITADA | 201 |
| CP-38 | PE | EM_USO | 409. **Não executável pela API**: não há endpoint que leve a reserva a EM_USO. Só com o estado preparado direto no banco. |

### 4.3 Conflito ao alterar horário

| ID | Técnica | Pré-condição e entrada | Resultado esperado |
|---|---|---|---|
| CP-39 | PE | E existe. P1 altera E para 10:00 às 11:30 (sobrepõe somente ela mesma) | 200 |
| CP-40 | PE | E (10:00 às 11:00) e F (12:00 às 13:00) existem em S1. Altera F para 10:30 às 12:30 | 409 |
| CP-41 | VL | E e F existem. Altera F para 11:00 às 12:00 (adjacente a E) | 200 |

## 5. Disponibilidade da sala (INV-05)

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-42 | PE | POST para S2 (EM_MANUTENCAO) | 409 |
| CP-43 | PE | POST para S3 (RESERVADA) | 409 |
| CP-44 | PE | POST com `salaId` inexistente (por exemplo 9999) | 404 |
| CP-45 | PE | Reserva SOLICITADA em S1; a sala passa a EM_MANUTENCAO (estado preparado); P1 altera o horário | 409 |

## 6. Solicitante, perfil e ordem das validações (INV-10)

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-46 | PE | POST com `solicitanteId` inexistente | 404 |
| CP-47 | PE | P1 (PROFESSOR) informa `tipoSolicitante` COORDENADOR | 403 |
| CP-48 | PE | C1 informa `tipoSolicitante` COORDENADOR | 201 |
| CP-49 | PE | C1 informa `tipoSolicitante` PROFESSOR | 403 |
| CP-50 | PE | Intervalo inválido e sala inexistente ao mesmo tempo | 400 (o intervalo é validado primeiro) |
| CP-51 | PE | Sala inexistente e solicitante inexistente ao mesmo tempo | 404 com mensagem citando a sala |
| CP-52 | PE | S2 (EM_MANUTENCAO) e solicitante inexistente ao mesmo tempo | 409 (a disponibilidade da sala vem antes do solicitante) |
| CP-53 | PE | E existe; P1 informa COORDENADOR no mesmo intervalo de E | 403 (o perfil é checado antes do conflito) |

Os casos CP-50 a CP-53 fixam a ordem das validações. Se a ordem mudar, os casos precisam ser revisados de propósito.

## 7. Decisão sobre a reserva (INV-04 e INV-09)

Pré-condição: reserva SOLICITADA de P1 em S1. Endpoints: POST `/aprovacao` e POST `/rejeicao`.

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-54 | PE | R1 aprova (tipoAprovador RESPONSAVEL) | 200, status APROVADA, `responsavelId` igual a R1, `administradorId` nulo, `aprovadoEm` preenchido |
| CP-55 | PE | A1 aprova (tipoAprovador ADMINISTRADOR) | 200, status APROVADA, `administradorId` igual a A1, `responsavelId` nulo. **Decisão pendente (RF10):** se só o Responsável puder aprovar, o esperado passa a ser recusa. |
| CP-56 | PE | R1 rejeita | 200, status REJEITADA, `responsavelId` igual a R1, `aprovadoEm` nulo |
| CP-57 | PE | A1 rejeita | 200, status REJEITADA, `administradorId` igual a A1 |
| CP-58 | PE | Aprova reserva inexistente | 404 |
| CP-59 | PE | Aprova com `usuarioId` de Responsável inexistente (tipo RESPONSAVEL) | 404 |
| CP-60 | PE | Aprova com `usuarioId` de Administrador inexistente (tipo ADMINISTRADOR) | 404 |
| CP-61 | PE | Aprova com o ID de R1 e tipo ADMINISTRADOR (sem administrador com esse ID) | 404 |
| CP-62 | PE | `tipoAprovador` fora do enum | 400 |
| CP-63 | PE | `tipoAprovador` ausente | 400 |
| CP-64 | PE | `usuarioId` ausente | 400 |
| CP-65 | VL | `usuarioId` igual a 0 | 400 |
| CP-66 | PE | Rejeita reserva inexistente | 404 |

## 8. Cancelamento e alteração por solicitante (INV-09 e INV-10)

| ID | Técnica | Pré-condição e entrada | Resultado esperado |
|---|---|---|---|
| CP-67 | PE | P1 cancela a própria reserva SOLICITADA | 200, status CANCELADA |
| CP-68 | PE | P1 cancela a própria reserva APROVADA | 200, status CANCELADA |
| CP-69 | PE | Após CP-67, P2 pede o mesmo intervalo na mesma sala | 201 (o cancelamento libera o horário) |
| CP-70 | PE | Após a rejeição da reserva, P2 pede o mesmo intervalo | 201 (a rejeição libera o horário) |
| CP-71 | PE | P2 cancela a reserva de P1 | 403. **Lacuna de contrato:** o OpenAPI não declara 403 para este endpoint. |
| CP-72 | PE | Cancela reserva inexistente | 404 |
| CP-73 | PE | Cancelamento com `usuarioId` ausente | 400 |
| CP-74 | VL | Cancelamento com `usuarioId` igual a 0 | 400 |
| CP-75 | PE | P2 altera o horário da reserva de P1 | 403. **Lacuna de contrato:** o OpenAPI não declara 403 para este endpoint. |
| CP-76 | PE | Altera horário de reserva inexistente | 404 |
| CP-77 | PE | Alteração com `solicitanteId` ausente | 400 |

## 9. Consulta (INV-12)

| ID | Técnica | Entrada | Resultado esperado |
|---|---|---|---|
| CP-78 | PE | GET de reserva existente | 200 com os campos do schema Reserva |
| CP-79 | PE | GET de reserva inexistente | 404 |
| CP-80 | PE | GET com ID não numérico (por exemplo abc) | 400. **[verificar]** o formato do corpo, que pode fugir do schema Erro. |
| CP-81 | PE | Lista de P1 com três reservas em horários diferentes | 200, ordenada por `inicio` do mais recente ao mais antigo |
| CP-82 | PE | Lista de solicitante sem reservas | 200 com lista vazia |
| CP-83 | PE | Lista de solicitante inexistente | 200 com lista vazia **[verificar]**: o contrato não define 404 |
| CP-84 | PE | Seguir o cabeçalho `Location` devolvido no 201 | O GET nessa URI responde 200 com a mesma reserva |

## 10. Contrato de resposta (INV-11 e INV-14)

| ID | Técnica | Verificação | Resultado esperado |
|---|---|---|---|
| CP-85 | PE | Respostas 400, 403, 404 e 409 de qualquer endpoint | O corpo tem o campo `mensagem` com texto não vazio |
| CP-86 | PE | Corpo do 201 de uma solicitação | Contém `id`, `salaId`, `solicitanteId`, `inicio`, `fim`, status SOLICITADA e `solicitadoEm` preenchido; `responsavelId`, `administradorId` e `aprovadoEm` nulos |
| CP-87 | PE | Campos de data nas respostas | Formato date-time conforme o contrato |
| CP-88 | PE | Revisão documental do `openapi.yaml` | O contrato declara 403 em cancelamento e alteração de horário. **Hoje não declara; é defeito de contrato.** |

## 11. Matriz de transição de estados (INV-09)

Cada linha parte de uma reserva no estado de origem e aplica uma operação. O resultado é o código HTTP e o estado final.

| ID | Estado de origem | Operação | Resultado esperado |
|---|---|---|---|
| TR-01 | SOLICITADA | Aprovar | 200, APROVADA |
| TR-02 | SOLICITADA | Rejeitar | 200, REJEITADA |
| TR-03 | SOLICITADA | Cancelar | 200, CANCELADA |
| TR-04 | SOLICITADA | Alterar horário | 200, continua SOLICITADA |
| TR-05 | APROVADA | Aprovar | 409 |
| TR-06 | APROVADA | Rejeitar | 409 |
| TR-07 | APROVADA | Cancelar | 200, CANCELADA |
| TR-08 | APROVADA | Alterar horário | 409 |
| TR-09 | REJEITADA | Aprovar | 409 |
| TR-10 | REJEITADA | Rejeitar | 409 |
| TR-11 | REJEITADA | Cancelar | 409 |
| TR-12 | REJEITADA | Alterar horário | 409 |
| TR-13 | CANCELADA | Aprovar | 409 |
| TR-14 | CANCELADA | Rejeitar | 409 |
| TR-15 | CANCELADA | Cancelar | 409 |
| TR-16 | CANCELADA | Alterar horário | 409 |

Os estados EM_USO, CONCLUIDA e NAO_COMPARECEU **não são alcançáveis pela API** hoje (RF15 está parcial). Não há transições executáveis a partir deles.

## 12. Tabela de decisão da aprovação (INV-04, RN05)

Cobre o perfil do aprovador e a classificação do recurso. **Decisão pendente (RF10):** a classificação de recurso restrito ainda não existe, então só as linhas de recurso não restrito podem ser executadas hoje.

| ID | Perfil do aprovador | Recurso restrito | Resultado esperado | Situação |
|---|---|---|---|---|
| TD-01 | Responsável | Não | Aprova (200) | Executável (equivale ao CP-54) |
| TD-02 | Administrador | Não | Aprova ou recusa, conforme a decisão do grupo | Executável (equivale ao CP-55) |
| TD-03 | Responsável | Sim | Aprova (200) | Bloqueado: não há classificação de recurso restrito |
| TD-04 | Administrador | Sim | Recusa, se a decisão for só o Responsável aprovar (RN05) | Bloqueado: não há classificação de recurso restrito |

## 13. Casos que não podem ser executados hoje

| Assunto | Motivo | Linha do inventário |
|---|---|---|
| Sobreposição de professor e material | Não fazem parte do modelo de reserva | INV-06 |
| Bloqueios e manutenção por período | Não há agenda nem endpoint | INV-07 |
| Autenticação e autorização por perfil | Não há autenticação; IDs e perfis vêm no corpo | INV-08 |
| Auditoria e não exclusão | Não há auditoria | INV-13 |
| Estados EM_USO, CONCLUIDA e NAO_COMPARECEU | Nenhum endpoint os alcança | INV-09 |
| Notificação | Não há cliente de notificação | INV-16 |

Estes itens permanecem registrados como **bloqueados ou pendentes**. Eles não podem ser reportados como aprovados.

## 14. Resumo

| Grupo | Quantidade |
|---|---|
| Casos de execução (CP-01 a CP-88) | 88 |
| Transições de estado (TR-01 a TR-16) | 16 |
| Tabela de decisão (TD-01 a TD-04) | 4 |
| Executáveis só com estado preparado no banco | CP-38 e CP-45 |
| Com decisão pendente | CP-55, TD-02, TD-03 e TD-04 |
| Com verificação adicional | CP-80 e CP-83 |
| Lacunas de contrato | CP-71, CP-75 e CP-88 |