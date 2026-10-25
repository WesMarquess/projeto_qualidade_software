# Casos de Teste de Caixa Branca

## 1. Escopo e premissas

Estes casos são derivados da **estrutura do código** em `src/main/java`. Cada decisão (`if`, `||`, `&&`, ternário, `orElseThrow`) recebe pelo menos um caso que a torna verdadeira e outro que a torna falsa, para cobrir ramos. Em condições compostas, há casos que variam cada condição separadamente (critério MC/DC simplificado).

- **Ferramentas previstas:** JUnit 5 com `@ParameterizedTest`, Mockito para isolar os repositórios e JaCoCo para medir linhas e ramos. As consultas JPQL são verificadas com PostgreSQL via Testcontainers.
- **Níveis:**
  - **U:** teste unitário do `ReservaService` com repositórios simulados (Mockito).
  - **E:** teste unitário direto na entidade, sem Spring.
  - **I:** teste de integração com PostgreSQL (Testcontainers).
  - **A:** teste pela API (REST-assured).
- **Linhas citadas** referem-se ao código atual. Se o arquivo mudar, as linhas devem ser revisadas.
- **A cobertura estrutural não substitui a evidência de requisito.** Cada caso cita o INV do inventário que ele ajuda a comprovar.

## 2. `ReservaService`

Arquivo: `src/main/java/br/com/projeto_qualidade_software/projeto/services/ReservaService.java`

### 2.1 Tabela de decisões

| ID | Método e linha | Decisão | Caso TRUE | Caso FALSE | Nível | INV |
|---|---|---|---|---|---|---|
| CB-01 | `solicitarReserva`, L72 | O tipo informado é diferente do tipo cadastrado do solicitante | Solicitante PROFESSOR com tipo COORDENADOR: lança `AcessoNegadoException` | Tipo igual ao cadastrado: segue o fluxo | U | INV-10 |
| CB-02 | `solicitarReserva`, L69 | Solicitante não encontrado (`orElseThrow`) | `findById` vazio: lança `EntityNotFoundException` | `findById` com solicitante: segue | U | INV-10 |
| CB-03 | `buscarSalaBloqueada`, L188 | Sala não encontrada | `buscarPorIdComBloqueio` vazio: lança `EntityNotFoundException` | Sala encontrada: segue | U | INV-05 |
| CB-04 | `validarDisponibilidadeDaSala`, L216 | O estado da sala é diferente de DISPONIVEL | Sala EM_MANUTENCAO e depois RESERVADA (duas partições): lança `IllegalStateException` | Sala DISPONIVEL: segue | U | INV-05 |
| CB-05 | `validarAusenciaDeConflito`, L229 | `contarConflitos` devolve mais que zero | Retorno 1 (limite): lança `IllegalStateException` | Retorno 0: segue | U | INV-02 |
| CB-06 | `alterarHorarioReserva`, L98 | O estado da reserva é diferente de SOLICITADA | Reserva APROVADA, CANCELADA e REJEITADA: lança `IllegalStateException` | Reserva SOLICITADA: segue | U | INV-09 |
| CB-07 | `buscarReservaBloqueada`, L181 | Reserva não encontrada | Repositório vazio: lança `EntityNotFoundException` | Reserva encontrada: segue | U | INV-09 |
| CB-08 | `validarSolicitante`, L208 | O solicitante informado é diferente do dono da reserva | ID diferente, e também ID nulo: lança `AcessoNegadoException` | ID igual: segue | U | INV-10 |
| CB-09 | `buscarReserva`, L169 | Reserva não encontrada | `findById` vazio: lança `EntityNotFoundException` | Reserva encontrada: devolve a reserva | U | INV-12 |
| CB-10 | `buscarResponsavel`, L195 | Responsável não encontrado | `findById` vazio: lança `EntityNotFoundException` | Responsável encontrado | U | INV-04 |
| CB-11 | `buscarAdministrador`, L202 | Administrador não encontrado | `findById` vazio: lança `EntityNotFoundException` | Administrador encontrado | U | INV-04 |

### 2.2 Condições compostas (MC/DC simplificado)

**`validarIntervalo`, L243:** `inicio == null || fim == null || !fim.isAfter(inicio)`

| ID | `inicio` | `fim` | Condição que decide | Resultado | Nível | INV |
|---|---|---|---|---|---|---|
| CB-12 | nulo | 10:00 | `inicio == null` verdadeira | `IllegalArgumentException` | U | INV-01 |
| CB-13 | 10:00 | nulo | `fim == null` verdadeira | `IllegalArgumentException` | U | INV-01 |
| CB-14 | 10:00 | 10:00 | `!fim.isAfter(inicio)` verdadeira (iguais) | `IllegalArgumentException` | U | INV-01 |
| CB-15 | 11:00 | 10:00 | `!fim.isAfter(inicio)` verdadeira (fim antes) | `IllegalArgumentException` | U | INV-01 |
| CB-16 | 10:00 | 10:01 | as três falsas | Não lança exceção | U | INV-01 |

**`cancelarReserva`, L117 e L118:** estado diferente de SOLICITADA `&&` estado diferente de APROVADA

| ID | Estado da reserva | Primeira condição | Segunda condição | Resultado | Nível | INV |
|---|---|---|---|---|---|---|
| CB-17 | SOLICITADA | falsa | (não avaliada) | Cancela, estado CANCELADA | U | INV-09 |
| CB-18 | APROVADA | verdadeira | falsa | Cancela, estado CANCELADA | U | INV-09 |
| CB-19 | CANCELADA, REJEITADA, EM_USO, CONCLUIDA, NAO_COMPARECEU | verdadeira | verdadeira | `IllegalStateException` | U (parametrizado) | INV-09 |

### 2.3 Escolha do tipo de aprovador

**`aprovarReserva`, L135, L137 e L139:** se RESPONSAVEL, senão se ADMINISTRADOR, senão erro.

| ID | `tipoAprovador` | Resultado | Nível | INV |
|---|---|---|---|---|
| CB-20 | RESPONSAVEL | Chama `buscarResponsavel` e `Reserva.aprovar(Responsavel)`; salva | U | INV-04 |
| CB-21 | ADMINISTRADOR | Chama `buscarAdministrador` e `Reserva.aprovar(Administrador)`; salva | U | INV-04 |
| CB-22 | nulo | Lança `AcessoNegadoException` | U | INV-04 |

**`rejeitarReserva`, L154, L156 e L158:** a mesma estrutura.

| ID | `tipoAprovador` | Resultado | Nível | INV |
|---|---|---|---|---|
| CB-23 | RESPONSAVEL | Chama `Reserva.rejeitar(Responsavel)`; salva | U | INV-04 |
| CB-24 | ADMINISTRADOR | Chama `Reserva.rejeitar(Administrador)`; salva | U | INV-04 |
| CB-25 | nulo | Lança `AcessoNegadoException` | U | INV-04 |

O `else` de CB-22 e CB-25 **não é alcançável pela API**: o DTO exige `tipoAprovador` não nulo e o enum só tem dois valores. Esses ramos só são cobertos por teste que chame o serviço direto.

### 2.4 Métodos sem decisão

`listarReservasDoSolicitante` não tem ramos. Cubra as linhas com dois casos de integração: lista vazia (solicitante sem reservas) e lista com reservas, verificando a ordem por início do mais recente ao mais antigo (INV-12).

## 3. `Reserva` (entidade)

Arquivo: `src/main/java/br/com/projeto_qualidade_software/projeto/entities/Reserva.java`

| ID | Método e linha | Decisão | Caso TRUE | Caso FALSE | Nível | INV |
|---|---|---|---|---|---|---|
| CB-26 | `aprovar(Responsavel)`, L93 | Responsável nulo | Passa nulo: `IllegalArgumentException` | Passa responsável | E | INV-04 |
| CB-27 | `aprovar(Administrador)`, L106 | Administrador nulo | Passa nulo: `IllegalArgumentException` | Passa administrador | E | INV-04 |
| CB-28 | `rejeitar(Responsavel)`, L119 | Responsável nulo | Passa nulo: `IllegalArgumentException` | Passa responsável | E | INV-04 |
| CB-29 | `rejeitar(Administrador)`, L132 | Administrador nulo | Passa nulo: `IllegalArgumentException` | Passa administrador | E | INV-04 |
| CB-30 | `validarSolicitada`, L145 | Estado diferente de SOLICITADA | Reserva em cada um dos 6 outros estados (parametrizado): `IllegalStateException` | Reserva SOLICITADA: segue | E | INV-09 |
| CB-31 | `aoCriar`, L78 | `solicitadoEm` nulo | Reserva nova sem data: o método preenche | Reserva com data já informada: preserva o valor | E ou I | INV-12 |
| CB-32 | `aoCriar`, L82 | `status` nulo | `setStatus(null)` e depois `aoCriar`: define SOLICITADA | Estado já informado: preserva o valor | E ou I | INV-09 |
| CB-33 | `isIntervaloValido`, L89 | `inicio == null`, `fim == null` ou `fim.isAfter(inicio)` | Início nulo, fim nulo ou fim posterior ao início: devolve verdadeiro | Fim igual ao início ou antes: devolve falso | E | INV-01 |

Efeitos verificados em CB-26 a CB-29 quando o argumento é válido:

- `aprovar`: o estado vira APROVADA, `aprovadoEm` é preenchido, o aprovador informado é gravado e o outro fica nulo.
- `rejeitar`: o estado vira REJEITADA, `aprovadoEm` continua nulo, o decisor informado é gravado e o outro fica nulo.

As checagens de nulo (CB-26 a CB-29) **não são alcançáveis pela API**, porque o serviço sempre busca o aprovador antes (e lança 404 se não achar). Só testes unitários diretos na entidade cobrem esses ramos.

CB-32 (`status == null`): o campo tem valor padrão SOLICITADA e o serviço também define o estado. Pelo fluxo normal, a condição nunca é verdadeira; só `setStatus(null)` a exercita. Anote como **ramo defensivo**.

## 4. `ReservaResponse.de`

Arquivo: `src/main/java/br/com/projeto_qualidade_software/projeto/api/dto/ReservaResponse.java`

| ID | Linha | Decisão | Caso TRUE | Caso FALSE | Nível | INV |
|---|---|---|---|---|---|---|
| CB-34 | L26 | `getResponsavel() == null` | Reserva sem responsável: `responsavelId` nulo | Reserva com responsável: `responsavelId` preenchido | E ou A | INV-14 |
| CB-35 | L27 | `getAdministrador() == null` | Reserva sem administrador: `administradorId` nulo | Reserva com administrador: `administradorId` preenchido | E ou A | INV-14 |

## 5. `ApiExceptionHandler`

Arquivo: `src/main/java/br/com/projeto_qualidade_software/projeto/api/ApiExceptionHandler.java`

Os handlers são lineares (sem `if`), então a cobertura vem de executar cada um.

| ID | Handler e linha | Exceção | Resposta esperada | Nível | INV |
|---|---|---|---|---|---|
| CB-36 | `tratarAcessoNegado`, L16 | `AcessoNegadoException` | 403 com `mensagem` | A ou U | INV-11 |
| CB-37 | `tratarNaoEncontrado`, L22 | `EntityNotFoundException` | 404 com `mensagem` | A ou U | INV-11 |
| CB-38 | `tratarArgumentoInvalido`, L28 | `IllegalArgumentException` | 400 com `mensagem` | A ou U | INV-11 |
| CB-39 | `tratarConflito`, L34 | `IllegalStateException` | 409 com `mensagem` | A ou U | INV-11 |
| CB-40 | `tratarValidacao`, L40 a L45 | `MethodArgumentNotValidException` com erro de campo | 400 com mensagem no formato `campo: motivo` | A | INV-11 |
| CB-41 | `tratarValidacao`, L44 (`orElse`) | Exceção sem nenhum erro de campo | 400 com a mensagem padrão "Os dados informados são inválidos." | U direto | INV-11 |
| CB-42 | `tratarCorpoInvalido`, L49 | `HttpMessageNotReadableException` | 400 com a mensagem de corpo ausente ou malformado | A | INV-11 |

CB-41 **não é alcançável pela API** com os DTOs atuais, que só têm validações de campo. Só o teste unitário direto, com um `BindingResult` sem erros de campo, cobre esse ramo.

## 6. `ReservaController`

Arquivo: `src/main/java/br/com/projeto_qualidade_software/projeto/api/ReservaController.java`

O controller não tem decisões: cada método só repassa dados ao serviço. A cobertura de linhas vem dos casos de caixa preta (CP-01 a CP-88). O ponto de atenção é a montagem do cabeçalho `Location` no `solicitar` (L38 a L42), verificado por CP-84.

## 7. Restrições de persistência (Bean Validation e banco)

| ID | Elemento | Restrição | Caso inválido | Caso válido | Nível | INV |
|---|---|---|---|---|---|---|
| CB-43 | `Sala.capacidade` | `@NotNull` e `@Min(20)` | Capacidade 19 (limite inferior inválido) e capacidade nula | Capacidade 20 (limite) | I | INV-05 |
| CB-44 | `Sala.statusSala` | `@NotNull` | Estado nulo | Qualquer estado do enum | I | INV-05 |
| CB-45 | `Solicitante.email` | `@Email` e coluna única | E-mail malformado; e-mail repetido | E-mail válido e único | I | INV-10 |
| CB-46 | `Responsavel.email` e `Administrador.email` | Coluna única | E-mail repetido | E-mail único | I | INV-04 |
| CB-47 | `Reserva.isIntervaloValido` | `@AssertTrue` | Salvar reserva com fim anterior ao início: violação de validação | Salvar reserva com intervalo válido | I | INV-01 |

## 8. Consultas JPQL (`ReservaRepository.contarConflitos`)

A consulta `contarConflitos` só pode ser verificada com banco real (Testcontainers com PostgreSQL). A condição tem quatro partes: mesma sala, estado entre os que ocupam, `inicio < :fim` e `fim > :inicio`, mais a exclusão opcional da própria reserva. Cada caso abaixo torna **uma** parte falsa e mantém as demais verdadeiras.

| ID | Parte da condição | Caso | Resultado esperado | INV |
|---|---|---|---|---|
| CB-48 | Sala | Reserva existente em outra sala, mesmo intervalo | Conta 0 | INV-02 |
| CB-49 | Estado | Reserva existente CANCELADA, mesmo intervalo | Conta 0 | INV-02 |
| CB-50 | Estado | Reserva existente REJEITADA, mesmo intervalo | Conta 0 | INV-02 |
| CB-51 | Estado | Reserva existente em cada um de SOLICITADA, APROVADA e EM_USO (parametrizado) | Conta 1 | INV-02 |
| CB-52 | `inicio < :fim` | Início da existente igual ao fim do novo (adjacente depois) | Conta 0 | INV-02 |
| CB-53 | `fim > :inicio` | Fim da existente igual ao início do novo (adjacente antes) | Conta 0 | INV-02 |
| CB-54 | Todas verdadeiras | Intervalos que se sobrepõem | Conta 1 | INV-02 |
| CB-55 | Exclusão da própria reserva | `reservaId` informado, igual ao ID da única reserva sobreposta | Conta 0 | INV-02 |
| CB-56 | Exclusão da própria reserva | `reservaId` nulo, uma reserva sobreposta | Conta 1 | INV-02 |
| CB-57 | Exclusão da própria reserva | `reservaId` de outra reserva, uma reserva sobreposta | Conta 1 | INV-02 |
| CB-58 | Ordenação | Três reservas do mesmo solicitante com inícios diferentes, consultadas por `findBySolicitanteIdOrderByInicioDesc` | Devolve do início mais recente ao mais antigo | INV-12 |

Verifique também as consultas de leitura:

- `findBySolicitanteIdOrderByInicioDesc` devolve a ordem do mais recente ao mais antigo (CB-58, nível I, INV-12).
- `buscarPorIdComBloqueio` das duas entidades aplica lock pessimista; o efeito é verificado nos testes de caixa cinza.

## 9. Código sem uso e ramos de difícil cobertura

| Item | Observação | Tratamento sugerido |
|---|---|---|
| `ReservaRepository.findByStatusOrderBySolicitadoEmAsc` | Nenhuma classe chama este método | Candidato a código morto. Remover ou justificar. |
| `SalaRepository.findByStatusSalaAndCapacidadeGreaterThanEqualOrderByCapacidadeAsc` | Nenhuma classe chama este método | Candidato a código morto. Remover ou justificar. |
| `findByEmailIgnoreCase` (Solicitante, Responsável, Administrador) | Nenhuma classe chama estes métodos; serão úteis quando houver autenticação | Manter com justificativa, ou testar quando houver uso. |
| `else` de `aprovarReserva` e `rejeitarReserva` | Inalcançável pela API (CB-22 e CB-25) | Cobrir com teste unitário direto. |
| Checagens de nulo em `aprovar` e `rejeitar` da `Reserva` | Inalcançável pela API (CB-26 a CB-29) | Cobrir com teste unitário direto na entidade. |
| `Reserva.aoCriar`, condição `status == null` | Ramo defensivo (CB-32) | Cobrir com `setStatus(null)`. |
| `ApiExceptionHandler.tratarValidacao`, `orElse` | Inalcançável com os DTOs atuais (CB-41) | Cobrir com teste unitário direto. |
| Redundância de validação | `validarIntervalo` (serviço) e `isIntervaloValido` (entidade) repetem a regra de INV-01 | Manter os dois, com o teste de cada um. |

Interfaces de repositório não geram código executável próprio, então não pesam no JaCoCo; o ponto é decidir se os métodos sem uso devem continuar existindo.

## 10. Complexidade ciclomática estimada

Estimativa por contagem de decisões (1 mais o número de pontos de decisão). O valor oficial é o do SonarCloud.

| Método | Pontos de decisão | Complexidade |
|---|---|---|
| `ReservaService.validarIntervalo` | 3 condições ligadas por `||` dentro de um `if` | 4 |
| `ReservaService.cancelarReserva` | 1 `if` e 1 `&&` | 3 |
| `ReservaService.aprovarReserva` | `if`, `else if` | 3 |
| `ReservaService.rejeitarReserva` | `if`, `else if` | 3 |
| `Reserva.aoCriar` | 2 `if` | 3 |
| `Reserva.isIntervaloValido` | 2 `||` | 3 |
| `ReservaResponse.de` | 2 ternários | 3 |
| `ReservaService.solicitarReserva` | 1 `if` | 2 |
| `ReservaService.alterarHorarioReserva` | 1 `if` | 2 |
| `validarSolicitante`, `validarDisponibilidadeDaSala`, `validarAusenciaDeConflito`, `validarSolicitada`, `aprovar` e `rejeitar` (4 sobrecargas) | 1 `if` cada | 2 |
| Demais métodos | nenhuma | 1 |

Nenhum método passa de complexidade 4. Os de maior risco para ramos são `validarIntervalo` e `cancelarReserva`, por terem condições compostas.

## 11. Como cada caso será medido no JaCoCo

- **Linha:** cada caso executa pelo menos uma linha nova, ou reforça uma já coberta com outro dado.
- **Ramo:** cada par TRUE/FALSE da tabela cobre os dois lados do desvio. Em `||` e `&&`, JaCoCo conta um desvio por condição; por isso há um caso por condição.
- **Estimativa de ramos nas classes de regra:** `ReservaService` em torno de 28 ramos, `Reserva` em torno de 18 e `ReservaResponse` 4. O número oficial vem do relatório do JaCoCo.
- **Exclusões propostas, cada uma com justificativa:** DTOs (`record` sem lógica), `ProjetoApplication`, classes de configuração e `DevDataInitializer`. Serviços, entidades com regra e o handler de erros **não** são excluídos.
- **Metas:** 80% de linhas e 70% de ramos no conjunto do projeto.

## 12. Classes de teste previstas

| Classe de teste | Casos | Nível |
|---|---|---|
| `ReservaServiceTest` | CB-01 a CB-25 | U (Mockito) |
| `ReservaTest` | CB-26 a CB-33 | E |
| `ReservaResponseTest` | CB-34 e CB-35 | E |
| `ApiExceptionHandlerTest` | CB-36 a CB-42 | U e A |
| `RestricoesPersistenciaIT` | CB-43 a CB-47 | I (Testcontainers) |
| `ReservaRepositoryIT` | CB-48 a CB-58 | I (Testcontainers) |

## 13. Resumo

| Grupo | Casos |
|---|---|
| Decisões do `ReservaService` | CB-01 a CB-25 |
| Entidade `Reserva` e `ReservaResponse` | CB-26 a CB-35 |
| `ApiExceptionHandler` | CB-36 a CB-42 |
| Restrições de persistência | CB-43 a CB-47 |
| Consultas JPQL | CB-48 a CB-58 |
| Total | 58 identificadores de caso |
| Ramos só alcançáveis por teste direto | CB-22, CB-25, CB-26 a CB-29, CB-32, CB-41 |