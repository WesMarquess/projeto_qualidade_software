# Relatório de desempenho — Apache JMeter

## 1. Objetivo e escopo

Este relatório consolida as execuções locais já realizadas contra a API de reservas, com aplicação Spring Boot executada separadamente e PostgreSQL 16 descartável. Não houve nova execução de carga para produzir este relatório.

Os cenários cobrem consultas, criação de reservas sem sobreposição acidental e uma rajada sincronizada para disputar a mesma sala e intervalo. Os resultados descrevem somente o ambiente local observado; não caracterizam capacidade de produção.

## 2. Ambiente e configuração observados

- Java 21, Spring Boot 3.5.6 e PostgreSQL 16 em contêiner dedicado.
- JMeter 5.6.3 executado localmente em modo não-GUI.
- API em `http://localhost:8080`; banco de carga publicado na porta local 5434 pelo projeto Compose `reservas-jmeter`.
- Seed da aplicação desativado; dados sintéticos preparados em banco descartável.
- Consultas e criação: 50 usuários virtuais, duração configurada de 300 segundos e ramp-up configurado de 30 segundos.
- Contenção: 50 threads, uma requisição por thread, liberadas pelo Synchronizing Timer numa única rajada; timeout do timer configurado em 60 segundos.

Os valores de 50 usuários, 5 minutos, p95 abaixo de 500 ms e taxa de erros abaixo de 1% são **metas experimentais provisórias**, não requisitos oficiais aprovados. Os resultados podem não atingir essas referências. A execução de contenção é uma rajada e não deve ser comparada diretamente aos testes sustentados.

## 3. Método de apuração

Os arquivos `statistics.json` produzidos nos relatórios HTML do JMeter são a fonte de média, mediana, mínimo, máximo, percentis e throughput. Nos relatórios JMeter usados, `pct1ResTime`, `pct2ResTime` e `pct3ResTime` representam p90, p95 e p99. As contagens, códigos HTTP, estados de sucesso e janelas observadas foram conferidos nos arquivos JTL; a janela foi obtida como o intervalo entre o menor timestamp de início de amostra HTTP e o maior timestamp de conclusão.

Throughput é expresso em requisições por segundo (`req/s`) conforme o relatório HTML. Para contenção, as métricas da tabela consideram somente as 50 amostras HTTP `Solicitar mesmo intervalo`, e não as duas amostras auxiliares do plano.

## 4. Resultados

### 4.1 Consultas — execução válida

Artefatos:

- JTL: `target/jmeter/consulta-corrigida.jtl`
- Relatório HTML: `target/jmeter/consulta-corrigida-html/`

| Medida | Total | Consultar reserva por ID | Listar reservas do solicitante |
|---|---:|---:|---:|
| Amostras HTTP | 386.167 | 193.093 | 193.074 |
| Janela observada | 299,920 s | incluída no total | incluída no total |
| Média | 36,64 ms | 34,31 ms | 38,97 ms |
| Mediana (p50) | 36 ms | 34 ms | 38 ms |
| p90 | 67 ms | 64 ms | 69 ms |
| p95 | 87 ms | 84 ms | 90 ms |
| p99 | 132 ms | 127 ms | 132 ms |
| Mínimo / máximo | 3 / 574 ms | 3 / 574 ms | 6 / 569 ms |
| Throughput | 1.287,57 req/s | 643,82 req/s | 643,96 req/s |
| Respostas esperadas / sucesso | 386.167 (100%) | 193.093 (100%) | 193.074 (100%) |
| Falhas técnicas ou respostas HTTP inesperadas | 0 | 0 | 0 |

As 386.167 respostas foram `200 OK` e passaram pelas asserções do plano; o relatório registra zero erros. O p95 observado de 87 ms ficou abaixo da referência experimental provisória de 500 ms. Isso não representa aprovação contra requisito oficial de desempenho, pois essa meta não foi aprovada como requisito.

### 4.2 Criação sem conflito

Artefatos:

- JTL: `target/jmeter/criacao.jtl`
- Relatório HTML: `target/jmeter/criacao-html/`

| Medida | Resultado |
|---|---:|
| Amostras HTTP | 40.490 |
| Janela observada | 298,863 s |
| Média | 351,36 ms |
| Mediana (p50) | 167 ms |
| p90 | 1.396 ms |
| p95 | 1.559 ms |
| p99 | 2.537,92 ms |
| Mínimo / máximo | 17 / 6.353 ms |
| Throughput | 135,48 req/s |
| Respostas esperadas / sucesso | 40.490 (100%) |
| Falhas técnicas ou respostas HTTP inesperadas | 0 |

As 40.490 respostas foram `201 Created`; a asserção do plano também verificou o cabeçalho `Location`. O relatório HTML registra zero erros. A execução criou intervalos separados por pelo menos uma hora, calculados por thread e iteração, para evitar colisões de horário acidentais. Os dados persistidos ao final da execução incluíam essas reservas, além da reserva de referência; o banco foi posteriormente descartado.

O p95 observado de 1.559 ms ficou acima da referência experimental provisória de 500 ms. Como essa referência não é um requisito oficial aprovado, não se declara aprovação ou reprovação oficial. O resultado indica que o fluxo de criação merece investigação e novas medições controladas antes de qualquer conclusão sobre desempenho.

### 4.3 Contenção — rajada única

Artefatos:

- JTL: `target/jmeter/contencao.jtl`
- Relatório HTML: `target/jmeter/contencao-html/`

| Medida das 50 amostras HTTP | Resultado |
|---|---:|
| Amostras HTTP | 50 |
| Janela observada | 983 ms |
| Média | 754,58 ms |
| Mediana (p50) | 767,5 ms |
| p90 | 950,2 ms |
| p95 | 969,15 ms |
| p99 | 983 ms |
| Mínimo / máximo | 522 / 983 ms |
| Throughput durante a rajada | 50,86 req/s |
| Resultados de negócio esperados | 50/50 (100%): 1 `201` e 49 `409` |
| Falhas técnicas ou respostas inesperadas | 0 |

O JTL registra exatamente uma resposta `201 Created` e 49 respostas `409 Conflict`. Os `409` são rejeições de negócio esperadas para as solicitações que perderam a disputa; o plano as contabiliza como resultados bem-sucedidos do cenário, não como erros técnicos. Não houve respostas inesperadas nas 50 amostras HTTP, e o relatório do JMeter apresenta zero erros.

O registro da sessão da execução informa que uma consulta ao PostgreSQL descartável encontrou uma reserva `SOLICITADA` persistida para o intervalo disputado. O banco de carga foi removido após o teste. Portanto, essa evidência é o resultado registrado na sessão; a contagem não foi consultada novamente durante esta consolidação.

Esta foi uma única rajada, sem repetição estatística. Os tempos e o throughput descrevem apenas essa disputa e não são diretamente comparáveis aos cenários sustentados nem à referência de p95 para carga contínua.

## 5. Execução diagnóstica descartada

Artefatos preservados:

- JTL: `target/jmeter/consulta-parametros-invalidos-diagnostico.jtl`
- Relatório HTML: `target/jmeter/consulta-parametros-invalidos-diagnostico-html/`

Essa primeira tentativa executou por uma janela observada de 299,799 segundos, com 457.504 amostras: 228.764 respostas `404` em `Consultar reserva por ID` e 228.740 respostas `200` em `Listar reservas do solicitante`. A taxa de erro reportada pelo JMeter foi 50,002625%, causada pela consulta ao ID inválido/fallback `0`; o relatório da execução identificou que os IDs reais não persistiram entre processos PowerShell.

Somente as 228.740 listagens passaram na asserção (49,997375% das amostras); as 228.764 consultas por ID falharam a asserção (50,002625%). Essas proporções descrevem a execução diagnóstica, não disponibilidade ou desempenho válido da API.

Este conjunto é exclusivamente diagnóstico e foi excluído da avaliação de desempenho válida. Seus arquivos são preservados, sem mistura com as estatísticas de consultas corrigidas.

## 6. Síntese e limitações

- Consultas: p95 total de 87 ms, 386.167 respostas `200` e zero erros observados no JTL.
- Criação: p95 de 1.559 ms, superior à referência experimental provisória; 40.490 respostas `201` e zero erros observados no JTL.
- Contenção: uma criação aceita e 49 conflitos de negócio esperados em rajada única; nenhuma falha técnica ou resposta inesperada registrada no JTL.
- Nenhuma dessas medições constitui critério oficial de aprovação ou reprovação de desempenho.

Limitações da evidência:

- As execuções ocorreram em ambiente local, sem caracterização de equivalência com produção.
- Não foram coletadas métricas de CPU, memória ou latência interna do PostgreSQL.
- Não há telemetria adicional de infraestrutura para analisar saturação, rede ou recursos do host.
- O cenário de contenção foi executado uma única vez; não há distribuição estatística entre repetições.
- O banco descartável foi removido; a evidência de persistência da contenção é o registro de saída da execução na sessão, não uma verificação repetida nesta fase.
- Os relatórios não permitem concluir a capacidade de produção nem atribuir a latência observada a um componente específico.

## 7. Reprodutibilidade e localização dos artefatos

Os comandos, parâmetros, preparação do banco isolado e execução dos planos estão em [README.md](README.md). Os arquivos JTL e relatórios HTML originais permanecem em `target/jmeter/`, associados aos cenários; nenhum resultado foi movido, renomeado ou reescrito para produzir este documento.

Esta entrega é somente a consolidação documental das execuções já realizadas, não uma nova execução de carga.
