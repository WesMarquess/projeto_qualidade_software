# Testes de carga com Apache JMeter

Este plano mede a API real de reservas usando JMeter 5.6.3, a aplicacao Spring Boot executada separadamente e PostgreSQL 16 dedicado. Nao use o banco de desenvolvimento nem um banco compartilhado.

O relatório das execuções já realizadas está em [relatorio-desempenho.md](relatorio-desempenho.md). Os JTL e relatórios HTML correspondentes permanecem em `target/jmeter/`:

| Cenário | JTL | Relatório HTML | Uso na consolidação |
|---|---|---|---|
| Consultas válidas | `consulta-corrigida.jtl` | `consulta-corrigida-html/` | Resultado válido com IDs reais. |
| Criação sem conflito | `criacao.jtl` | `criacao-html/` | Resultado do POST com intervalos distintos. |
| Contenção | `contencao.jtl` | `contencao-html/` | Rajada única; `409` esperados não são erros técnicos. |
| Consultas com parâmetros inválidos | `consulta-parametros-invalidos-diagnostico.jtl` | `consulta-parametros-invalidos-diagnostico-html/` | Diagnóstico descartado da avaliação de desempenho, preservado para rastreabilidade. |

Os valores iniciais de **50 usuarios virtuais**, **5 minutos**, **p95 abaixo de 500 ms** e **menos de 1% de erros** sao metas experimentais provisórias, nao requisitos oficiais aprovados. Os resultados podem nao atingir esses valores; registre-os sem alterar os dados para aparentar aprovacao.

## Pre-requisitos

- Java 21 e Maven Wrapper disponiveis.
- Docker com o plugin `docker compose`.
- Apache JMeter 5.6.3 instalado independentemente (nao e dependencia Maven).
- Aplicacao compilada e ambiente local livre das portas 8080 e 5434.

### Instalar JMeter 5.6.3

No Windows PowerShell, baixe o arquivo binario da versao fixada na distribuicao oficial do Apache e extraia-o fora do repositorio:

```powershell
$archive = Join-Path $env:TEMP "apache-jmeter-5.6.3.zip"
$tools = Join-Path $env:USERPROFILE "tools"
New-Item -ItemType Directory -Force $tools | Out-Null
Invoke-WebRequest "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.zip" -OutFile $archive
Expand-Archive -Path $archive -DestinationPath $tools -Force
$env:JMETER_HOME = Join-Path $tools "apache-jmeter-5.6.3"
$env:PATH = "$env:JMETER_HOME\bin;$env:PATH"
jmeter --version
```

Confirme que o comando indica 5.6.3. Em Linux/macOS, baixe o mesmo arquivo da distribuicao oficial e extraia-o fora do repositorio. Nao baixe ou execute binarios de fontes nao oficiais.

## Preparacao isolada

Execute os comandos a partir da raiz do repositorio. O nome do projeto Compose e sempre `reservas-jmeter`; o banco fica na porta do host 5434, nao usa volume persistente e nao compartilha o banco local da porta 5432 ou o contêiner de diagnostico em 5433.

```powershell
docker compose -p reservas-jmeter -f docker-compose.jmeter.yml up -d db
```

Inicie a aplicacao em outro terminal, conectando-a exclusivamente ao banco de carga e desativando os dados seed:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5434/reservas_carga"
$env:DB_USER = "reservas_carga"
$env:DB_PASSWORD = "reservas_carga_local"
$env:APP_SEED_ENABLED = "false"
.\mvnw.cmd -B spring-boot:run
```

Espere a aplicacao estar pronta em `http://localhost:8080`. O Hibernate cria/atualiza o schema conforme a configuracao de desenvolvimento. Em outro terminal, aplique o SQL somente depois da inicializacao do schema:

```powershell
Get-Content -Raw docs/testes/jmeter/preparar-dados.sql | docker compose -p reservas-jmeter -f docker-compose.jmeter.yml exec -T db psql -v ON_ERROR_STOP=1 -U reservas_carga -d reservas_carga -f -
```

O script interrompe com erro se encontrar salas, solicitantes ou reservas preexistentes; nao apaga dados. Ele cria 50 salas disponiveis, um solicitante sintetico e uma reserva de referencia. Obtenha os IDs efetivamente gerados pelo PostgreSQL:

```powershell
New-Item -ItemType Directory -Force target | Out-Null
$fixtureRows = docker compose -p reservas-jmeter -f docker-compose.jmeter.yml exec -T db psql -At -F "," -U reservas_carga -d reservas_carga -c "SELECT sala.id, solicitante.id FROM salas sala CROSS JOIN solicitantes solicitante WHERE solicitante.email = 'jmeter-carga@teste.local' ORDER BY sala.id"
$fixtureRows | Set-Content -Encoding ascii target/jmeter-fixtures.csv
$reference = docker compose -p reservas-jmeter -f docker-compose.jmeter.yml exec -T db psql -At -F "," -U reservas_carga -d reservas_carga -c "SELECT reserva.id, reserva.sala_id, reserva.solicitante_id FROM reservas reserva JOIN solicitantes solicitante ON solicitante.id = reserva.solicitante_id WHERE solicitante.email = 'jmeter-carga@teste.local' AND reserva.inicio = TIMESTAMP '2040-01-01 09:00:00'"
$reservaId, $salaId, $solicitanteId = $reference.Split(",")
```

Os IDs sao os valores reais devolvidos pelo banco descartavel, sem depender de sequencias ou IDs predefinidos. Crie a pasta de resultados antes de executar:

```powershell
New-Item -ItemType Directory -Force target/jmeter | Out-Null
```

O CSV sem cabecalho (`salaId,solicitanteId`) e consumido pelo plano de criacao; as variaveis `$reservaId`, `$salaId` e `$solicitanteId` sao usadas nos exemplos seguintes.

## Cenarios e execucao

Os planos sao executados em modo nao-GUI. `-Jhost`, `-Jport`, `-Jusers` e `-JdurationSeconds` sao configuraveis; os planos de consulta e criacao usam 50 usuarios e 300 segundos como valores iniciais.

### Consultas

O plano faz `GET /api/reservas/{reservaId}` e `GET /api/reservas/solicitante/{solicitanteId}`, validando `200 OK`. Informe os IDs retornados pelo SQL:

```powershell
jmeter -n -t docs/testes/jmeter/reservas-consulta.jmx -Jhost=localhost -Jport=8080 -Jusers=50 -JdurationSeconds=300 "-JreservaId=$reservaId" "-JsolicitanteId=$solicitanteId" -l target/jmeter/consulta.jtl -e -o target/jmeter/consulta-html
```

### Criacao sem conflito

O plano envia `POST /api/reservas` com os campos do DTO real (`salaId`, `solicitanteId`, `tipoSolicitante`, `inicio`, `fim`), valida `201 Created` e que `Location` esteja presente. Cada requisicao calcula um intervalo distinto de uma hora, separado por pelo menos outra hora; o indice combina thread e iteracao. Assim, usuarios podem compartilhar as salas sinteticas sem conflitos de intervalo acidentais. O `inicioBase` pode ser alterado com `-JinicioBase=2050-01-01T00:00:00`.

```powershell
jmeter -n -t docs/testes/jmeter/reservas-criacao.jmx -Jhost=localhost -Jport=8080 -Jusers=50 -JdurationSeconds=300 -JinicioBase=2050-01-01T00:00:00 -l target/jmeter/criacao.jtl -e -o target/jmeter/criacao-html
```

O plano usa por padrao o CSV `target/jmeter-fixtures.csv` gerado na preparacao. Escolha um novo `inicioBase` para cada execucao sobre o mesmo banco; o plano nao apaga nem cancela reservas criadas.

### Contencao por sala e intervalo

Este e um disparo unico: 50 threads usam o mesmo `salaId`, solicitante e intervalo ainda livre. Um Synchronizing Timer libera as requisicoes juntas. A estrategia bloqueia a linha da sala e verifica conflitos nos estados `SOLICITADA`, `APROVADA` e `EM_USO`; em banco isolado sem reserva previa nesse intervalo, espera-se uma resposta `201` e 49 respostas `409`. O plano contabiliza esses conflitos como respostas de negocio esperadas e falha a amostra final se a contagem nao for exatamente essa ou ocorrer qualquer outra resposta/erro tecnico.

```powershell
jmeter -n -t docs/testes/jmeter/reservas-contencao.jmx -Jhost=localhost -Jport=8080 "-JsalaId=$salaId" "-JsolicitanteId=$solicitanteId" -l target/jmeter/contencao.jtl -e -o target/jmeter/contencao-html
```

Execute contencao em banco recem-preparado e apenas uma vez. A reserva aceita ocupa o intervalo; repetir no mesmo banco nao e uma repeticao equivalente. Para refazer, descarte e recrie somente o projeto Compose dedicado, reinicie a aplicacao e repita a preparacao de dados.

## Metricas e resultados

Cada plano grava um arquivo `.jtl` e um relatorio HTML em `target/jmeter/<cenario>-html`. Registre no relatorio da execucao:

- usuarios, duracao, ramp-up, URL, data/hora e configuracao do ambiente;
- quantidade de amostras, taxa de requisicoes e tempos medio, mediano, p90, p95 e p99;
- codigos HTTP, erros tecnicos, respostas inesperadas e, em contencao, contagens separadas de `201` e `409`;
- uso de CPU/memoria da aplicacao, host e PostgreSQL, se medido externamente.

Consulta/criacao marcam status HTTP diferente do esperado como falha da amostra. Em contencao, o plano conta `409` como resposta de negocio esperada e nao como erro tecnico; erro de transporte, `4xx` diferente de `409`, `5xx` ou contagem divergente e falha tecnica/de cenario. Preserve JTL e HTML originais; nao reporte uma execucao que nao ocorreu como resultado.

## Limpeza segura

Pare a aplicacao e descarte exclusivamente o banco sem volume do projeto Compose reservado para carga:

```powershell
docker compose -p reservas-jmeter -f docker-compose.jmeter.yml down -v
```

Esse comando remove os recursos desse projeto Compose; nao o execute se outro ambiente tiver reutilizado o mesmo nome de projeto. Nao use `down` contra o `docker-compose.yml` de desenvolvimento, nao limpe tabelas manualmente e nao aponte os comandos acima para um banco compartilhado.
