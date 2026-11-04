-- V5 · Catálogo inicial de loterías colombianas (CP-20).
--
-- Solo los NOMBRES y, como referencia, el día habitual de cada sorteo (1 = lunes … 7 = domingo). Salieron de los
-- resultados publicados en agosto de 2026. NO se siembran horas: ninguna fuente consultada las traía.
-- Los días son orientativos: cuando el sorteo cae en festivo la lotería puede jugar otro día o no jugar
-- (ver docs/CP-07-INFORME-LOTERIAS.md). La fecha real la fija cada rifa y el resultado lo carga y verifica una
-- persona antes de sortear, así que un día mal sembrado no afecta ningún sorteo.
--
-- Es idempotente: no duplica una lotería que ya exista (se compara sin distinguir mayúsculas) ni toca las
-- que creaste a mano. Si alguna ya no juega, desactívala desde la API (PUT /api/admin/loterias/{id}).
-- Los chances (Dorado, Paisita, Chontico…) no se siembran: se agregan con POST /api/admin/loterias.
INSERT INTO loterias (nombre, tipo, dias_semana, activa)
SELECT v.nombre, 'LOTERIA', v.dias, TRUE
FROM (VALUES
    ('Lotería de Cundinamarca', '1'),
    ('Lotería del Tolima',      '1'),
    ('Lotería de la Cruz Roja', '2'),
    ('Lotería del Huila',       '2'),
    ('Lotería de Manizales',    '3'),
    ('Lotería del Meta',        '3'),
    ('Lotería del Valle',       '3'),
    ('Lotería de Bogotá',       '4'),
    ('Lotería de Risaralda',    '5'),
    ('Lotería de Santander',    '5'),
    ('Lotería de Medellín',     '5'),
    ('Lotería de Boyacá',       '6'),
    ('Lotería del Cauca',       '6')
) AS v(nombre, dias)
WHERE NOT EXISTS (SELECT 1 FROM loterias l WHERE lower(l.nombre) = lower(v.nombre));
