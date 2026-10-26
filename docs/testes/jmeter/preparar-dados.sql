BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM salas)
       OR EXISTS (SELECT 1 FROM solicitantes)
       OR EXISTS (SELECT 1 FROM reservas) THEN
        RAISE EXCEPTION
            'Banco de carga nao esta vazio. Descarte somente o projeto Compose dedicado e recrie o banco.';
    END IF;
END
$$;

INSERT INTO salas (capacidade, status_sala)
SELECT 30, 'DISPONIVEL'
FROM generate_series(1, 50);

INSERT INTO solicitantes (
    name,
    email,
    password,
    tipo_solicitante,
    criado_em,
    atualizado_em
)
VALUES (
    'JMeter Carga',
    'jmeter-carga@teste.local',
    'senha-sintetica',
    'PROFESSOR',
    CURRENT_DATE,
    CURRENT_DATE
);

INSERT INTO reservas (sala_id, solicitante_id, inicio, fim, status, solicitado_em)
SELECT sala.id,
       solicitante.id,
       TIMESTAMP '2040-01-01 09:00:00',
       TIMESTAMP '2040-01-01 10:00:00',
       'SOLICITADA',
       CURRENT_TIMESTAMP
FROM salas sala
CROSS JOIN solicitantes solicitante
WHERE solicitante.email = 'jmeter-carga@teste.local'
ORDER BY sala.id
LIMIT 1;

SELECT sala.id AS sala_id,
       solicitante.id AS solicitante_id,
       reserva.id AS reserva_id
FROM salas sala
CROSS JOIN solicitantes solicitante
JOIN reservas reserva
  ON reserva.sala_id = sala.id
 AND reserva.solicitante_id = solicitante.id
WHERE solicitante.email = 'jmeter-carga@teste.local'
  AND reserva.inicio = TIMESTAMP '2040-01-01 09:00:00'
ORDER BY sala.id;

COMMIT;
