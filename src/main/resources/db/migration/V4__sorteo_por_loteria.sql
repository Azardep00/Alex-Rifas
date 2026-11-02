-- V4 (CP-09): sorteo por lotería. Cada ganador guarda QUÉ resultado verificado lo decidió, para poder
-- auditarlo y para impedir que se anule después un resultado que ya adjudicó un premio.
-- Compatible con filas existentes: las columnas nuevas son nullable o tienen DEFAULT (los ganadores de
-- sorteos ALEATORIO/MANUAL quedan sin resultado asociado).

ALTER TABLE ganadores
    ADD COLUMN resultado_loteria_id   BIGINT  REFERENCES resultados_loteria(id),
    -- Número que salió de la lotería para este premio (últimos N dígitos). Puede diferir del boleto ganador
    -- cuando el cupo estaba vacío y se aplicó ADJUDICAR_SIGUIENTE_VENDIDO.
    ADD COLUMN numero_resultado       INTEGER CHECK (numero_resultado IS NULL OR numero_resultado >= 0),
    ADD COLUMN adjudicado_por_vacante BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ganadores
    ADD CONSTRAINT chk_ganador_resultado CHECK (
        (resultado_loteria_id IS NULL AND numero_resultado IS NULL AND NOT adjudicado_por_vacante)
        OR (resultado_loteria_id IS NOT NULL AND numero_resultado IS NOT NULL)
    );

CREATE INDEX idx_ganadores_resultado ON ganadores (resultado_loteria_id) WHERE resultado_loteria_id IS NOT NULL;
