# Fluxos - Diagramas de Sequencia

Diagramas iniciais dos fluxos criticos. Os participantes representam papeis e responsabilidades, sem definir arquitetura tecnica.

## 1. Solicitar reserva e validar disponibilidade (RF06-RF11, RN01-RN05)

```mermaid
sequenceDiagram
    actor S as Solicitante
    participant APP as Aplicacao
    actor R as Responsavel

    S->>APP: Informa intervalo e recursos desejados
    APP->>APP: Valida inicio, termino, bloqueios e sobreposicoes
    alt Intervalo invalido, conflito ou recurso indisponivel
        APP-->>S: Recusa solicitacao e informa o motivo
    else Dados validos
        APP->>APP: Registra solicitacao no estado SOLICITADA
        opt Recurso classificado como restrito
            APP-->>R: Disponibiliza solicitacao para analise
            R->>APP: Aprova ou rejeita
            APP->>APP: Atualiza estado e registra auditoria
            APP-->>S: Informa a decisao
        end
    end
```

**Pendente:** o fluxo de aprovacao para recursos nao restritos e a regra que determina quando uma solicitacao passa a `APROVADA` ainda precisam ser definidos.

## 2. Duas solicitacoes simultaneas para o mesmo recurso (RF08-RF09, RN02-RN03)

```mermaid
sequenceDiagram
    actor S1 as Solicitante A
    actor S2 as Solicitante B
    participant APP as Aplicacao
    participant AGENDA as Agenda de recursos

    par Solicitacoes simultaneas
        S1->>APP: Solicita recurso R no intervalo T
    and
        S2->>APP: Solicita recurso R no intervalo T
    end
    alt Solicitacao de A processada primeiro
        APP->>AGENDA: Valida e registra a solicitacao de A
        AGENDA-->>APP: A aceita
        APP->>AGENDA: Valida a solicitacao de B
        AGENDA-->>APP: B conflita com a reserva de A
        APP-->>S1: Confirma a solicitacao de A
        APP-->>S2: Recusa a solicitacao conflitante de B
    else Solicitacao de B processada primeiro
        APP->>AGENDA: Valida e registra a solicitacao de B
        AGENDA-->>APP: B aceita
        APP->>AGENDA: Valida a solicitacao de A
        AGENDA-->>APP: A conflita com a reserva de B
        APP-->>S2: Confirma a solicitacao de B
        APP-->>S1: Recusa a solicitacao conflitante de A
    end
```

O mecanismo interno de concorrencia nao e prescrito neste documento; o criterio e nao aceitar duas reservas conflitantes. A suite de testes deve provar o comportamento com solicitacoes simultaneas.

## 3. Retirada e devolucao de material (RF13, RN08)

```mermaid
sequenceDiagram
    actor R as Responsavel
    participant APP as Aplicacao

    R->>APP: Consulta material associado a reserva
    R->>APP: Registra retirada
    APP->>APP: Registra evento e horario da retirada
    R->>APP: Registra devolucao
    APP->>APP: Registra evento e horario da devolucao
    APP-->>R: Exibe movimentacao atualizada
```

## Cobertura dos fluxos

| Fluxo | Requisitos | Regras de negocio |
|---|---|---|
| Solicitacao e disponibilidade | RF06-RF11 | RN01-RN05 |
| Concorrencia de reserva | RF08-RF09 | RN02-RN03 |
| Movimentacao de material | RF13-RF14 | RN07-RN08 |