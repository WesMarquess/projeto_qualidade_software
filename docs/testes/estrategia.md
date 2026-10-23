# Estrategia de Testes

## 1. Escopo

Esta estrategia orienta a verificacao do backend atual de reservas e a evolucao dos testes conforme os requisitos do PRD forem implementados. O escopo funcional atual e um recorte de reservas de salas; a matriz abaixo nao considera requisito pendente como comportamento existente.

Estao no escopo de verificacao da API descrita em `docs/openapi.yaml`:

- `POST /api/reservas`: solicitar reserva;
- `GET /api/reservas/{reservaId}` e `GET /api/reservas/solicitante/{solicitanteId}`: consultar reservas;
- `PUT /api/reservas/{reservaId}/horario`: alterar horario;
- `POST /api/reservas/{reservaId}/cancelamento`: cancelar;
- `POST /api/reservas/{reservaId}/aprovacao` e `POST /api/reservas/{reservaId}/rejeicao`: decidir solicitacao.

Os cenarios prioritarios rastreiam RF07 a RF11 e as regras RN01 a RN05, conforme criterio academico. RF07 esta implementado; RF08 a RF11 tem cobertura parcial ou lacunas descritas na matriz da secao 4. RF06 pode ser coberto nos fluxos implementados de solicitacao, alteracao e cancelamento.

Ficam fora da validacao funcional da implementacao atual: autenticacao e autorizacao efetivas (RF01-RF02), cadastros e pesquisa geral (RF03-RF05), conflitos de professor/material, classificacao de recursos restritos, manutencao por periodo, validacao de alocacao docente, movimentacao de materiais, auditoria, ciclo completo de estados, relatorios e interface responsiva. Esses itens permanecem requisitos do PRD, mas nao devem ser reportados como aprovados por testes de API que nao os exercitam.

## 2. Niveis e ferramentas

| Nivel | Ferramenta | Aplicacao neste projeto |
|---|---|---|
| Unitario | JUnit 5, incluindo `@ParameterizedTest` | Verificar validacao de intervalo e transicoes de `Reserva.aprovar`/`Reserva.rejeitar`; cobrir casos validos, invalidos e limites sem iniciar a aplicacao. Isolar repositorios com doubles de teste. |
| Integracao | JUnit 5 + Testcontainers com PostgreSQL | Exercitar servico, repositorios, transacoes, consultas de conflito e locks usando o banco real da estrategia. Verificar `ReservaRepository.contarConflitos`, `buscarPorIdComBloqueio` e `SalaRepository.buscarPorIdComBloqueio`. |
| API caixa-preta | REST-assured | Enviar HTTP pelos endpoints publicados e validar codigos, cabecalhos, corpos e erros definidos no OpenAPI, sem depender da estrutura interna das classes. |
| E2E | REST-assured + aplicacao completa + PostgreSQL em Testcontainers | Executar jornadas pela API desde a solicitacao ate consulta e, quando aplicavel, alteracao, cancelamento ou decisao, confirmando o estado persistido. Este nivel cobre o fluxo backend; nao implica teste de navegador. |
| Concorrencia | JUnit 5 + REST-assured + Testcontainers com PostgreSQL | Disparar solicitacoes conflitantes em paralelo para a mesma sala e intervalo; conferir resultado final no banco e evidenciar que nao houve dupla reserva (RF09/RN03). |
| Carga | Apache JMeter | Medir os fluxos de consulta e solicitacao da API e entregar plano e resultado de carga, como previsto no PRD. Nao ha meta numerica de carga ou latencia definida ainda. |
| Integracao simulada | WireMock | Quando o escopo de notificacao for confirmado, simular a dependencia HTTP externa e verificar a integracao sem chamar um servico real. RF19 esta pendente e nao ha cliente ou endpoint de notificacao no codigo atual. |

Os testes de API devem cobrir os resultados documentados no OpenAPI, incluindo `201` na solicitacao aceita e erros `400`, `403`, `404` ou `409` quando aplicaveis. A lista exata de resultados esperados deve respeitar o contrato e as regras definidas, sem acrescentar codigos ou campos obrigatorios nao documentados.

## 3. Caixa-preta, branca e cinza

**Caixa-preta:** usar o OpenAPI como contrato observavel, sem inspecionar chamadas internas. REST-assured deve exercitar, por exemplo, `POST /api/reservas` com intervalo valido e invalido, `GET /api/reservas/{reservaId}` para recurso existente/inexistente e `PUT /api/reservas/{reservaId}/horario` para alteracao permitida ou rejeitada. A resposta deve ser comparada ao contrato publicado. A ausencia atual de autenticacao nao pode ser interpretada como autorizacao validada.

**Caixa-branca:** orientar casos por decisoes e ramos reais do codigo. Em `ReservaService`, cobrir `validarIntervalo`, `validarDisponibilidadeDaSala`, `validarAusenciaDeConflito`, verificacao de solicitante e os ramos de aprovador. Em `Reserva`, verificar a transicao de estado e a rejeicao de decisao quando o estado nao e `SOLICITADA`. Em `ApiExceptionHandler`, verificar o mapeamento das excecoes para HTTP. A cobertura estrutural e medida pelo JaCoCo; ela nao substitui a evidencia de requisito.

**Caixa-cinza:** combinar conhecimento do desenho interno com observacao externa, sem substituir a API por chamadas diretas ao servico. Usar REST-assured contra a aplicacao conectada a PostgreSQL do Testcontainers, sabendo que `SalaRepository.buscarPorIdComBloqueio` usa lock pessimista e que `ReservaRepository.contarConflitos` aplica a regra de sobreposicao para salas. Esse nivel e essencial para RF09/RN03: um mock ou banco em memoria nao demonstra o comportamento do lock no PostgreSQL.

As classes de referencia sao [ReservaController](../../src/main/java/br/com/projeto_qualidade_software/projeto/api/ReservaController.java), [ReservaService](../../src/main/java/br/com/projeto_qualidade_software/projeto/services/ReservaService.java), [Reserva](../../src/main/java/br/com/projeto_qualidade_software/projeto/entities/Reserva.java), [ReservaRepository](../../src/main/java/br/com/projeto_qualidade_software/projeto/repositories/ReservaRepository.java), [SalaRepository](../../src/main/java/br/com/projeto_qualidade_software/projeto/repositories/SalaRepository.java) e [ApiExceptionHandler](../../src/main/java/br/com/projeto_qualidade_software/projeto/api/ApiExceptionHandler.java).

## 4. Criterios de saida e rastreabilidade critica

Para a suite de verificacao, os criterios academicos de saida sao:

- cobertura minima de 80% de linhas e 70% de branches no JaCoCo;
- zero defeito critico em aberto na avaliacao final;
- 100% dos requisitos criticos RF07 a RF11 registrados na RTM, cada um ligado a cenarios e evidencia/status de execucao;
- plano e resultado de teste de carga entregues, sem declarar aprovado um limite de desempenho ainda nao definido.

A presenca na RTM nao significa que um requisito esteja implementado ou aprovado. Requisito bloqueado deve permanecer identificado como bloqueado/pendente, sem evidencia de teste passar em seu lugar.

| Requisito / regra | Cenario e evidencia a rastrear | Situacao conhecida no backend |
|---|---|---|
| RF07 / RN01 | Criar e alterar reserva com termino posterior; rejeitar inicio/fim ausentes ou termino igual/anterior ao inicio. Validar resposta HTTP e regra de intervalo. | Implementado em `ReservaService.validarIntervalo`; DTOs tambem validam campos obrigatorios. |
| RF08 / RN02 | Para a mesma sala, verificar intervalos sobrepostos e nao sobrepostos e os estados que ocupam agenda. Registrar separadamente os cenarios de professor e material quando forem suportados. | Parcial: `ReservaRepository.contarConflitos` considera apenas sala; professor e material nao estao no modelo de reserva atual. |
| RF09 / RN03 | Submeter em paralelo duas solicitacoes para a mesma sala e intervalo contra PostgreSQL; exigir evidencia de no maximo uma reserva aceita e nenhuma dupla reserva. | Parcial: ha lock pessimista na sala durante o fluxo, mas falta teste de integracao concorrente com o banco-alvo. |
| RF10 / RN05 | Rastrear a regra de aprovacao de recurso restrito, incluindo aprovador permitido e decisao resultante, somente apos a classificacao de recurso e os papeis aceitos serem definidos. | Pendente/parcial: nao ha classificacao de recurso restrito; o codigo aceita `RESPONSAVEL` ou `ADMINISTRADOR` na decisao e nao autentica o chamador. Nao marcar como aprovado. |
| RF11 / RN04 | Tentar solicitar reserva para sala indisponivel e verificar recusa. Separar indisponibilidade por estado de sala de bloqueio/manutencao por periodo. | Parcial: o servico recusa sala cujo estado nao seja `DISPONIVEL`; nao ha agenda de bloqueios ou manutencoes por periodo. |

Os relatorios devem mostrar o resultado por requisito (passou, falhou, bloqueado ou nao executado) e manter a ligacao entre RF/RN, caso de teste e evidencia. A classificacao de defeito critico precisa seguir criterio aprovado pela equipe; ela nao e definida nos documentos lidos.

## 5. Ambiente e dados de teste

- Runtime alvo informado pelo projeto: Java 21, Spring Boot 3 e Maven. O contrato OpenAPI usa `http://localhost:8080` como servidor local.
- O ambiente de integracao deve usar PostgreSQL descartavel via Testcontainers, pois o teste de concorrencia depende do comportamento de locks do banco. A versao da imagem deve corresponder ao banco-alvo quando este for definido. E necessario um runtime de containers compativel com Testcontainers.
- A base de teste deve ser isolada e reinicializada entre cenarios ou suites. Usar somente registros sinteticos, sem credenciais reais.
- Preparar salas nos estados `DISPONIVEL`, `RESERVADA` e `EM_MANUTENCAO`; solicitantes dos tipos `PROFESSOR` e `COORDENADOR`; aprovadores `RESPONSAVEL` e `ADMINISTRADOR`; e reservas com estados e intervalos necessarios aos cenarios. Respeitar as restricoes de persistencia existentes, como capacidade minima de 20 para `Sala`.
- Para conflitos, preparar intervalos coincidentes, sobrepostos e adjacentes; datas devem ser geradas de forma consistente durante a execucao. O teste concorrente deve compartilhar a mesma sala/intervalo entre as requisicoes e consultar o resultado persistido.
- O repositorio ainda nao tem `pom.xml`, wrapper `mvnw` ou suite de testes Java identificada. Portanto, a estrategia e uma orientacao para implementacao futura; JaCoCo, Testcontainers e os demais testes ainda nao podem ser executados neste estado.

## 6. Riscos da estrategia

- Sem build Maven e suite Java, nao ha execucao automatizada integrada nem medicao verificavel de cobertura.
- RF08, RF10 e RF11 excedem o comportamento atualmente implementado; testar apenas o recorte existente pode criar falsa impressao de conformidade com o PRD.
- Um banco em memoria ou mocks para repositorios nao reproduzem necessariamente locks e concorrencia do PostgreSQL; por isso, nao servem como unica evidencia de RF09.
- Os identificadores e papeis enviados nas requisicoes nao comprovam identidade. Testes com IDs conhecidos nao validam autenticacao nem autorizacao real.
- Nao ha cliente de notificacao; WireMock nao tera integracao concreta para verificar ate RF19 ter escopo e contrato.
- Nao foram definidos volume de usuarios, taxa de requisicoes, duracao, latencia-alvo ou ambiente de carga. Resultados do JMeter sem esses parametros sao apenas medidas observadas, nao aprovacao de desempenho.
- O E2E proposto cobre a jornada HTTP/backend. A cobertura automatizada da interface web requer decisao de escopo e ferramenta especifica.
- `solicitadoEm` e `aprovadoEm` usam o relogio do sistema; asserts dependentes de horario exato podem ser instaveis.

## Perguntas em aberto

- Qual banco e versao representam o ambiente-alvo, e qual runtime de containers estara disponivel na CI?
- Quando serao disponibilizados `pom.xml`, `mvnw`, dependencias de teste e configuracao JaCoCo? Quais classes, se houver, podem ser excluidas justificadamente das metricas?
- Em RF10, como um recurso sera classificado como restrito e somente `RESPONSAVEL` podera aprovar, ou `ADMINISTRADOR` tambem tera permissao? Como isso se relaciona ao endpoint de aprovacao atual?
- Como RF08 deve abranger conflitos de professor e material, alem de sala?
- Quais bloqueios e periodos de manutencao devem ser testados em RF11, inclusive em relacao a reservas existentes?
- RF19 sera uma notificacao simulada ou uma integracao externa? Qual evento, payload e resultado observavel devem ser verificados com WireMock?
- Qual e a carga-alvo para JMeter (usuarios concorrentes, taxa, duracao, latencia e ambiente)?
- E2E deve incluir navegacao na interface web ou a jornada completa pela API e banco atende ao criterio? Se houver navegador, qual ferramenta deve ser adotada?
- Quais sao a matriz de ambientes/navegadores e os criterios de defeito critico para a avaliacao?
- O contrato OpenAPI deve declarar propriedades obrigatorias da resposta `Reserva` e exemplos de erro mais especificos?