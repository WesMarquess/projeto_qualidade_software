# Revisão de segurança

**Escopo:** revisão estática do projeto Java/Spring Boot, com foco em autenticação, autorização, exposição de dados e operações da API.  
**Resultado:** foi identificado um achado de severidade média. Este relatório não representa certificação nem afirma que o sistema possui autenticação ou segurança completa.

## Achado

| # | Severidade | Arquivo | Linhas | Vulnerabilidade | Confiança |
|---|---|---|---|---|---|
| 1 | 🟡 MÉDIA | `src/main/java/br/com/projeto_qualidade_software/projeto/api/ReservaController.java` | 58-63 | `GET /api/reservas/pendentes` é público e retorna todas as reservas solicitadas, incluindo IDs de sala e solicitante, horários e estado, sem verificar identidade ou autorização. | 9/10 |

O endpoint chama `ReservaService.listarReservasPendentes()`, que consulta as reservas no estado `SOLICITADA` (`src/main/java/br/com/projeto_qualidade_software/projeto/services/ReservaService.java`, método `listarReservasPendentes`). A resposta inclui identificadores e dados de horário (`ReservaResponse`). Como não há mecanismo de autenticação/autorização que estabeleça a identidade do chamador, não é possível restringir esta consulta a um perfil confiável.

**Impacto:** um cliente com acesso à API pode enumerar reservas pendentes e obter informações operacionais de agendamento e identificadores relacionados.

**Recomendação:** não expor a listagem a clientes não autorizados. Reavaliar a rota quando houver um mecanismo confiável de identidade e autorização; não confiar em IDs ou papéis declarados pelo próprio cliente como prova de identidade.

## Limitações e escopo

- O achado acima é o resultado reportado pela revisão de código; não foram implementadas correções nesta etapa, conforme a opção selecionada de documentar e commitar o resumo.
- O projeto não deve ser descrito como autenticado ou plenamente seguro enquanto não houver autenticação e autorização confiáveis.
- A revisão estática não substitui testes dinâmicos, análise de configuração de produção nem uma verificação independente de dependências contra avisos de segurança atualizados.
