# Sistema de Alocacao de Recursos Universitarios - PRD

**Status:** Rascunho para validacao  
**Versao:** 0.1  
**Contexto:** Projeto academico para uso no contexto universitario.

## Objetivo

Desenvolver e validar uma aplicacao para organizar a alocacao de salas, professores e materiais sem conflitos de horario. O produto deve permitir que professores e coordenadores consultem disponibilidade e gerenciem reservas, enquanto Responsaveis e Administradores executam as atribuicoes de perfil descritas neste PRD.

## Contexto

O projeto e academico e ainda nao existe uma aplicacao para esse processo. A especificacao da disciplina exige demonstrar nao apenas funcionamento, mas tambem qualidade, seguranca, rastreabilidade e desempenho. Os requisitos sao derivados da especificacao fornecida e das responsabilidades de perfil confirmadas pelo solicitante.

## Perfis

| Perfil | Responsabilidade principal |
|---|---|
| Solicitante | Professor ou coordenador que consulta disponibilidade e cria, altera ou cancela suas reservas. |
| Responsavel | Aprova solicitacoes especiais, valida a alocacao de docentes e acompanha retiradas e devolucoes de materiais. |
| Administrador | Gerencia salas, professores, materiais, usuarios, bloqueios e periodos de manutencao. |

## Escopo inicial

- Autenticacao e autorizacao por perfil.
- Cadastro e consulta de salas, professores e materiais.
- Busca por tipo, capacidade, localizacao, competencia e disponibilidade.
- Criacao, alteracao, cancelamento, aprovacao e acompanhamento de reservas.
- Prevencao de conflito para sala, material e professor, inclusive sob solicitacoes simultaneas.
- Bloqueios, manutencao, retirada/devolucao de materiais e historico auditavel.
- Relatorios de utilizacao, carga horaria alocada e conflitos evitados.

## Situacao atual do backend

O backend existente e um recorte inicial de reservas de salas, nao a implementacao completa deste escopo. Na solicitacao, valida se `tipoSolicitante` (`PROFESSOR`/`COORDENADOR`) corresponde ao cadastro; decisoes aceitam `RESPONSAVEL`/`ADMINISTRADOR` com ID existente e registram o decisor. Isso nao substitui autenticacao: IDs e papeis enviados pelo cliente ainda podem ser falsificados. A protecao concorrente usa bloqueio pessimista e ainda precisa de teste de integracao. Recursos restritos, conflitos de professor/material, auditoria, ciclo completo de estados, manutencao por periodo, movimentacao de materiais e operacoes administrativas continuam pendentes. Consulte a [matriz de implementacao dos requisitos](prd/requisitos-funcionais.md#situacao-da-implementacao-atual) e a [documentacao da API](api-reservas.md).

## Criterios academicos de sucesso

- Nenhuma reserva conflitante aceita, inclusive em solicitacoes simultaneas.
- Recursos em manutencao indisponiveis para novas reservas.
- 100% dos requisitos criticos ligados a risco, teste e evidencia na RTM.
- Cobertura minima de 80% de linhas e 70% de branches, conforme a especificacao da disciplina.
- Zero defeitos ou vulnerabilidades criticas na avaliacao final.
- Plano e resultado de teste de carga, estrategia de testes e relatorio de qualidade entregues.

Os percentuais e entregaveis acima sao criterios da disciplina, nao limites definidos pela ISO.

## Modelo de qualidade

Os requisitos nao funcionais sao organizados pelo modelo de qualidade de produto da **ISO/IEC 25010:2023**, usado como referencia para especificacao e validacao, sem alegacao de certificacao ou conformidade. A edicao deve ser confirmada com a equipe docente caso o curso determine outra versao.

Consulte [requisitos nao funcionais e evidencias ISO 25010](prd/requisitos-nao-funcionais.md).

## Documentos do PRD

- [Requisitos funcionais e rastreabilidade](prd/requisitos-funcionais.md)
- [Requisitos nao funcionais ISO 25010](prd/requisitos-nao-funcionais.md)
- [Fluxos e diagramas de sequencia](prd/fluxos-sequencia.md)
- Modelagem estática: [diagrama de componentes](prd/modelagem/estatica/componentes.md) e [diagrama de classes](prd/modelagem/estatica/classes.md)
- [Documentacao da API de reservas e contrato OpenAPI](api-reservas.md)
- Personas: [Administrador](prd/personas/admin-sistema.md), [Responsavel](prd/personas/responsavel.md) e [Solicitante](prd/personas/solicitante.md)

## Referencias

- [Especificacao do projeto da disciplina](https://afonsolelis.github.io/aulas_senac/pages/qualidade2/especificacao-projeto.html)
- [Repositorio de referencia para organizacao do PRD](https://github.com/afonsolelis/cinesenac)

## Pendencias de validacao

- Criterios de aprovacao para reservas nao restritas e definicao de quais recursos sao restritos.
- Limites de horario, antecedencia, duracao, recorrencia e cancelamento de reservas.
- Como bloqueios afetam reservas existentes e quais alteracoes podem ocorrer apos o inicio.
- Metas numericas de desempenho, plataforma/ambientes suportados e nivel de acessibilidade.
- Escopo da integracao de notificacoes e aplicabilidade de requisitos de seguranca operacional (safety).
- Confirmacao da edicao ISO 25010 exigida pela disciplina.
