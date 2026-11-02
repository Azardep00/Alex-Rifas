-- V3 (CP-08): catálogo de loterías/chances, resultados con verificación y vínculo desde la rifa.
-- Compatible con filas existentes: todo lo nuevo es nullable o tiene DEFAULT.

-- ---------------------------------------------------------------------------
-- loterias: cada FILA es un sorteo que se puede usar para jugar una rifa.
--   Una lotería tradicional juega 1 vez por semana; un chance (ej. "Dorado Noche") juega a diario,
--   por eso cada franja del chance es su propia fila. dias_semana y hora_sorteo son solo valores de
--   referencia (ISO: 1=lunes … 7=domingo); la fecha real del sorteo la fija la rifa.
-- ---------------------------------------------------------------------------
CREATE TABLE loterias (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(80)  NOT NULL,
    tipo              VARCHAR(10)  NOT NULL DEFAULT 'LOTERIA' CHECK (tipo IN ('LOTERIA', 'CHANCE')),
    dias_semana       VARCHAR(20),
    hora_sorteo       TIME,
    sitio_oficial_url VARCHAR(300),
    activa            BOOLEAN      NOT NULL DEFAULT TRUE,
    creada_en         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_loteria_dias CHECK (dias_semana IS NULL OR dias_semana ~ '^[1-7](,[1-7]){0,6}$')
);
CREATE UNIQUE INDEX uq_loterias_nombre ON loterias (lower(nombre));

-- ---------------------------------------------------------------------------
-- resultados_loteria: número que salió en un sorteo.
--   numero_mayor es TEXTO de 4 dígitos (existen resultados como 0377; un entero perdería el cero).
--   Flujo: PENDIENTE_VERIFICACION → VERIFICADO (solo estos se usarán para sortear, CP-09) → ANULADO.
--   Una corrección se hace anulando y cargando de nuevo; nunca se borra.
-- ---------------------------------------------------------------------------
CREATE TABLE resultados_loteria (
    id               BIGSERIAL PRIMARY KEY,
    loteria_id       BIGINT       NOT NULL REFERENCES loterias(id),
    fecha_sorteo     DATE         NOT NULL,
    numero_mayor     VARCHAR(4)   NOT NULL CHECK (numero_mayor ~ '^[0-9]{4}$'),
    serie            VARCHAR(3)   CHECK (serie IS NULL OR serie ~ '^[0-9]{1,3}$'),
    numero_sorteo    INTEGER      CHECK (numero_sorteo IS NULL OR numero_sorteo > 0),
    fuente           VARCHAR(120) NOT NULL,
    evidencia_url    VARCHAR(500),
    notas            VARCHAR(500),
    estado           VARCHAR(25)  NOT NULL DEFAULT 'PENDIENTE_VERIFICACION'
                     CHECK (estado IN ('PENDIENTE_VERIFICACION', 'VERIFICADO', 'ANULADO')),
    cargado_por      BIGINT       REFERENCES usuarios(id) ON DELETE SET NULL,
    verificado_por   BIGINT       REFERENCES usuarios(id) ON DELETE SET NULL,
    verificado_en    TIMESTAMPTZ,
    anulado_por      BIGINT       REFERENCES usuarios(id) ON DELETE SET NULL,
    anulado_en       TIMESTAMPTZ,
    motivo_anulacion VARCHAR(500),
    version          BIGINT       NOT NULL DEFAULT 0,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_resultado_verificado CHECK (estado <> 'VERIFICADO' OR verificado_en IS NOT NULL),
    CONSTRAINT chk_resultado_anulado    CHECK (estado <> 'ANULADO' OR (anulado_en IS NOT NULL AND motivo_anulacion IS NOT NULL))
);
-- Un solo resultado vigente por sorteo; uno anulado no estorba para cargar el correcto.
CREATE UNIQUE INDEX uq_resultado_vigente ON resultados_loteria (loteria_id, fecha_sorteo)
    WHERE estado <> 'ANULADO';
CREATE INDEX idx_resultados_loteria_fecha ON resultados_loteria (loteria_id, fecha_sorteo DESC);

-- ---------------------------------------------------------------------------
-- rifas: vínculo con la lotería (loteria_nombre queda como texto heredado para no romper la API actual).
-- ---------------------------------------------------------------------------
ALTER TABLE rifas ADD COLUMN loteria_id BIGINT REFERENCES loterias(id);

-- Rifas existentes: se crea la lotería a partir del nombre escrito y se enlaza.
INSERT INTO loterias (nombre)
SELECT DISTINCT ON (lower(btrim(loteria_nombre))) btrim(loteria_nombre)
FROM rifas
WHERE loteria_nombre IS NOT NULL AND btrim(loteria_nombre) <> ''
ON CONFLICT DO NOTHING;

UPDATE rifas r SET loteria_id = l.id
FROM loterias l
WHERE r.loteria_nombre IS NOT NULL AND lower(btrim(r.loteria_nombre)) = lower(l.nombre);

CREATE INDEX idx_rifas_loteria ON rifas (loteria_id) WHERE loteria_id IS NOT NULL;
