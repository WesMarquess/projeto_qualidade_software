# API de Reservas

**Status:** Implementacao inicial
**Versao do contrato:** 1.0.0
**Contrato OpenAPI:** [openapi.yaml](openapi.yaml)

## Escopo

A API expoe a consulta das salas cadastradas e os fluxos de reserva implementados: solicitacao, consulta, listagem por solicitante, alteracao de horario, cancelamento, aprovacao e rejeicao. O contrato OpenAPI pode ser aberto no Swagger Editor ou carregado por uma instalacao Swagger UI.

O projeto ainda nao possui arquivo Maven/Gradle nem configuracao do Springdoc. Portanto, este contrato documenta a API no formato consumido pelo Swagger, mas a interface Swagger UI nao esta publicada pela aplicacao.

## Validacao das regras de negocio

Esta API implementa apenas parte das regras previstas no PRD. O comportamento observado no backend e:

| Regra | Situacao atual |
|---|---|
| RN01 - Intervalo | Implementada: o termino precisa ser posterior ao inicio. |
| RN02 - Conflito de horario | Parcial: a verificacao cobre somente a sala, nao professores ou materiais. Intervalos adjacentes sao aceitos. |
| RN03 - Concorrencia | Parcial: a solicitacao bloqueia a linha da sala com lock pessimista dentro de transacao; ainda falta teste de integracao concorrente com o banco usado pela aplicacao. |
| RN04 - Bloqueio/manutencao | Parcial: a sala so e aceita quando `DISPONIVEL`; nao existem bloqueios por periodo nem gestao administrativa desses estados. |
| RN05 - Aprovacao restrita | Parcial: a decisao aceita somente `RESPONSAVEL` ou `ADMINISTRADOR` e exige ID existente no cadastro correspondente. Ainda nao ha classificacao de recurso restrito para exigir aprovacao somente nesses casos. |
| RN06 - Ciclo de estados | Parcial: a API permite `SOLICITADA` para `APROVADA` ou `REJEITADA`, e cancelamento de solicitadas/aprovadas. Nao implementa transicoes para `EM_USO`, `CONCLUIDA` ou `NAO_COMPARECEU`. |
| RN07 - Protecao e auditoria | Parcial: a API nao oferece rota de exclusao, mas permite cancelar reservas `APROVADA` sem verificar se o horario de inicio ja passou. Tambem nao registra historico auditavel. O repository continua expondo operacoes de persistencia, portanto a regra nao e uma garantia do modelo inteiro. |
| RN08 - Materiais | Nao implementada: nao ha entidades nem rotas para retirada e devolucao. |

Na criacao, o payload precisa incluir `tipoSolicitante` (`PROFESSOR` ou `COORDENADOR`), e o backend exige correspondencia com o tipo salvo para o `solicitanteId`. Aprovacao/rejeicao exige `tipoAprovador` (`RESPONSAVEL` ou `ADMINISTRADOR`) e ID existente no cadastro indicado; a resposta identifica quem decidiu. Cargo e IDs ainda sao enviados pelo cliente e podem ser falsificados: essa validacao de consistencia nao substitui autenticacao/autorizacao baseada em identidade. Consulta por ID e listagem por `solicitanteId` tambem nao verificam propriedade autenticada.

## Rotas

| Metodo | Rota | Finalidade | Sucesso |
|---|---|---|---|
| `GET` | `/api/salas` | Listar salas cadastradas com ID, capacidade e estado | `200 OK` |
| `POST` | `/api/reservas` | Solicitar reserva | `201 Created` |
| `GET` | `/api/reservas/{reservaId}` | Consultar reserva | `200 OK` |
| `GET` | `/api/reservas/solicitante/{solicitanteId}` | Listar reservas do solicitante | `200 OK` |
| `GET` | `/api/reservas/pendentes` | Listar reservas `SOLICITADA`, ordenadas por data da solicitação crescente | `200 OK` |
| `PUT` | `/api/reservas/{reservaId}/horario` | Alterar horario enquanto solicitada | `200 OK` |
| `POST` | `/api/reservas/{reservaId}/cancelamento` | Cancelar reserva solicitada ou aprovada | `200 OK` |
| `POST` | `/api/reservas/{reservaId}/aprovacao` | Aprovar reserva solicitada | `200 OK` |
| `POST` | `/api/reservas/{reservaId}/rejeicao` | Rejeitar reserva solicitada | `200 OK` |

A consulta de salas retorna uma lista, que pode estar vazia. Cada item contém somente `id`, `capacidade` e `statusSala`; não inclui os demais dados da entidade.

A listagem de reservas pendentes retorna somente reservas com status `SOLICITADA`, ordenadas por `solicitadoEm` em ordem crescente. Se não houver pendências, retorna `200 OK` com `[]`. Os itens mantêm o formato `ReservaResponse`.

## Exemplos

Datas devem ser enviadas em ISO 8601, por exemplo `2026-10-20T09:00:00`.

### Solicitar

```http
POST /api/reservas
Content-Type: application/json

{
  "salaId": 1,
  "solicitanteId": 12,
  "tipoSolicitante": "PROFESSOR",
  "inicio": "2026-10-20T09:00:00",
  "fim": "2026-10-20T10:30:00"
}
```

Retorna `201 Created`, inclui a URI da reserva no cabecalho `Location` e representa a reserva no estado `SOLICITADA`.

### Alterar horario

```http
PUT /api/reservas/42/horario
Content-Type: application/json

{
  "solicitanteId": 12,
  "inicio": "2026-10-20T10:00:00",
  "fim": "2026-10-20T11:30:00"
}
```

### Cancelar, aprovar ou rejeitar

Cancelamento recebe o ID do solicitante:

```http
POST /api/reservas/42/cancelamento
Content-Type: application/json

{
  "usuarioId": 12
}
```

Aprovacao e rejeicao recebem o ID e o papel do aprovador. O ID deve existir no cadastro correspondente:

```json
{
  "usuarioId": 4,
  "tipoAprovador": "ADMINISTRADOR"
}
```

`tipoAprovador` aceita `RESPONSAVEL` ou `ADMINISTRADOR`. A resposta informa o decisor em `responsavelId` ou `administradorId`.

## Respostas de erro

Os erros retornam JSON no formato `{"mensagem":"..."}`.

| Status | Significado |
|---|---|
| `400 Bad Request` | Corpo invalido, campos obrigatorios ausentes ou intervalo incorreto |
| `404 Not Found` | Reserva, sala, solicitante ou responsavel inexistente |
| `403 Forbidden` | Cargo informado nao corresponde ao tipo salvo para o solicitante |
| `409 Conflict` | Sobreposicao de horario ou estado que nao permite a operacao |

O intervalo exige `fim` posterior a `inicio`. Reservas solicitadas, aprovadas ou em uso ocupam a sala; salas indisponiveis nao aceitam novas solicitacoes.

## Seguranca e limites atuais

Autenticacao/autorizacao vinculadas a identidade ainda nao estao implementadas. O backend verifica os papeis declarados contra os tipos permitidos e os IDs existentes, mas qualquer cliente ainda pode declarar o ID de outra pessoa. Consultas por ID/listagem tambem nao filtram pelo usuario autenticado. Nao exponha esta API a usuarios ou redes nao confiaveis antes de integrar autenticacao e autorizacao.

O modelo atual permite verificar conflito apenas de sala. Conflitos com professores e materiais, historico auditavel e classificacao de recursos restritos dependem de entidades e regras ainda ausentes. A nova entidade de Administrador e o relacionamento de aprovacao podem exigir migracao do schema do banco.

## Referencias

- [Requisitos funcionais](prd/requisitos-funcionais.md)
- [Fluxo de solicitacao de reserva](prd/fluxos-sequencia.md)
- [Especificacao do projeto](https://afonsolelis.github.io/aulas_senac/pages/qualidade2/especificacao-projeto.html)