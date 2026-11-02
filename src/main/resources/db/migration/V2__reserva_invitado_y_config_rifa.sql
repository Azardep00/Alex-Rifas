-- V2 (CP-02): reserva de invitado (sin cuenta) y configuración por rifa para sorteos por lotería.
-- Compatible con filas existentes: todo lo nuevo es nullable o tiene DEFAULT.

-- ---------------------------------------------------------------------------
-- compras: comprador invitado (nombre + teléfono) y usuario_id opcional
-- ---------------------------------------------------------------------------
ALTER TABLE compras
    ADD COLUMN comprador_nombre   VARCHAR(120),
    ADD COLUMN comprador_telefono VARCHAR(30);

ALTER TABLE compras
    ALTER COLUMN usuario_id DROP NOT NULL;

-- Toda compra debe poder atribuirse a alguien: una cuenta o un teléfono de contacto.
ALTER TABLE compras
    ADD CONSTRAINT chk_compra_comprador
        CHECK (usuario_id IS NOT NULL OR comprador_telefono IS NOT NULL);

-- Consulta "mi reserva" por código + teléfono, y tope de pendientes por teléfono (CP-05).
CREATE INDEX idx_compras_telefono_estado ON compras(comprador_telefono, estado)
    WHERE comprador_telefono IS NOT NULL;

-- ---------------------------------------------------------------------------
-- rifas: duración de la reserva, dígitos de lotería y regla de cupo vacío
-- ---------------------------------------------------------------------------
ALTER TABLE rifas
    ADD COLUMN reserva_minutos INTEGER     NOT NULL DEFAULT 15,
    ADD COLUMN digitos         INTEGER,
    ADD COLUMN regla_vacante   VARCHAR(30) NOT NULL DEFAULT 'REPETIR_SIGUIENTE_LOTERIA';

ALTER TABLE rifas
    ADD CONSTRAINT chk_rifa_reserva_minutos CHECK (reserva_minutos BETWEEN 5 AND 1440),
    ADD CONSTRAINT chk_rifa_digitos CHECK (digitos IS NULL OR digitos IN (2, 3, 4)),
    ADD CONSTRAINT chk_rifa_regla_vacante
        CHECK (regla_vacante IN ('REPETIR_SIGUIENTE_LOTERIA', 'ADJUDICAR_SIGUIENTE_VENDIDO')),
    -- ADR-06: con "últimos N dígitos", cantidad_boletos debe ser exactamente 10^N
    -- (100, 1000, 10000) para que el resultado mapee 1 a 1 a un boleto.
    ADD CONSTRAINT chk_rifa_digitos_cantidad CHECK (
        digitos IS NULL OR cantidad_boletos = CASE digitos WHEN 2 THEN 100 WHEN 3 THEN 1000 WHEN 4 THEN 10000 END
    );
