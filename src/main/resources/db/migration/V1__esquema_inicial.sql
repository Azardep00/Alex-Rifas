-- Esquema base de Alex-Rifas. Flyway es la única fuente de cambios de esquema en Docker/producción.
CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    telefono VARCHAR(30) NOT NULL,
    correo VARCHAR(160) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('ADMIN','ORGANIZADOR','CLIENTE')),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_nacimiento DATE,
    acepto_terminos_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_usuarios_rol_activo ON usuarios(rol, activo);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expira_en TIMESTAMPTZ NOT NULL,
    revocado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens(usuario_id, revocado);

CREATE TABLE rifas (
    id BIGSERIAL PRIMARY KEY,
    codigo_publico VARCHAR(16) NOT NULL UNIQUE,
    organizador_id BIGINT NOT NULL REFERENCES usuarios(id),
    titulo VARCHAR(120) NOT NULL,
    descripcion VARCHAR(4000),
    imagen_url VARCHAR(500),
    precio_boleto NUMERIC(12,2) NOT NULL CHECK (precio_boleto > 0),
    moneda VARCHAR(3) NOT NULL DEFAULT 'COP',
    cantidad_boletos INTEGER NOT NULL CHECK (cantidad_boletos >= 10),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('BORRADOR','PUBLICADA','CERRADA','SORTEADA','CANCELADA')),
    fecha_sorteo TIMESTAMPTZ NOT NULL,
    cierre_ventas_en TIMESTAMPTZ NOT NULL,
    maximo_boletos_por_compra INTEGER NOT NULL CHECK (maximo_boletos_por_compra >= 1),
    metodo_sorteo VARCHAR(20) NOT NULL CHECK (metodo_sorteo IN ('ALEATORIO','LOTERIA','MANUAL')),
    loteria_nombre VARCHAR(80),
    numero_autorizacion VARCHAR(60),
    entidad_autorizadora VARCHAR(120),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicada_en TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_rifa_fechas CHECK (fecha_sorteo > cierre_ventas_en),
    CONSTRAINT chk_rifa_maximo CHECK (maximo_boletos_por_compra <= cantidad_boletos)
);
CREATE INDEX idx_rifas_estado_fecha ON rifas(estado, fecha_sorteo);
CREATE INDEX idx_rifas_organizador_creacion ON rifas(organizador_id, creada_en DESC);

CREATE TABLE premios (
    id BIGSERIAL PRIMARY KEY,
    rifa_id BIGINT NOT NULL REFERENCES rifas(id) ON DELETE CASCADE,
    posicion INTEGER NOT NULL CHECK (posicion >= 1),
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(1000),
    valor_estimado NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (valor_estimado >= 0),
    imagen_url VARCHAR(500),
    CONSTRAINT uq_premio_posicion UNIQUE (rifa_id, posicion)
);

-- Las tablas de compra/pago/ganador dejan lista la estructura para la siguiente etapa del proyecto.
CREATE TABLE compras (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(32) NOT NULL UNIQUE,
    rifa_id BIGINT NOT NULL REFERENCES rifas(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','EXPIRADA','CANCELADA')),
    subtotal NUMERIC(14,2) NOT NULL CHECK (subtotal >= 0),
    expira_en TIMESTAMPTZ NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_compras_usuario_fecha ON compras(usuario_id, creada_en DESC);
CREATE INDEX idx_compras_estado_expira ON compras(estado, expira_en);

CREATE TABLE boletos (
    id BIGSERIAL PRIMARY KEY,
    rifa_id BIGINT NOT NULL REFERENCES rifas(id) ON DELETE CASCADE,
    numero INTEGER NOT NULL CHECK (numero >= 0),
    estado VARCHAR(15) NOT NULL CHECK (estado IN ('DISPONIBLE','RESERVADO','VENDIDO')),
    compra_id BIGINT REFERENCES compras(id) ON DELETE SET NULL,
    reservado_hasta TIMESTAMPTZ,
    CONSTRAINT uq_boleto_numero_rifa UNIQUE (rifa_id, numero)
);
CREATE INDEX idx_boletos_rifa_estado ON boletos(rifa_id, estado);
CREATE INDEX idx_boletos_compra ON boletos(compra_id);

CREATE TABLE pagos_wompi (
    id BIGSERIAL PRIMARY KEY,
    compra_id BIGINT NOT NULL REFERENCES compras(id),
    referencia VARCHAR(100) NOT NULL UNIQUE,
    transaccion_id VARCHAR(100) UNIQUE,
    estado VARCHAR(30) NOT NULL,
    monto NUMERIC(14,2) NOT NULL CHECK (monto >= 0),
    moneda VARCHAR(3) NOT NULL DEFAULT 'COP',
    respuesta_json JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ganadores (
    id BIGSERIAL PRIMARY KEY,
    rifa_id BIGINT NOT NULL REFERENCES rifas(id),
    premio_id BIGINT NOT NULL REFERENCES premios(id),
    boleto_id BIGINT NOT NULL REFERENCES boletos(id),
    publicado BOOLEAN NOT NULL DEFAULT FALSE,
    resultado_json JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ganador_premio UNIQUE (premio_id),
    CONSTRAINT uq_ganador_boleto UNIQUE (boleto_id)
);

CREATE TABLE auditoria_eventos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    entidad VARCHAR(80) NOT NULL,
    entidad_id VARCHAR(80),
    accion VARCHAR(80) NOT NULL,
    request_id VARCHAR(80),
    ip_origen INET,
    detalles_json JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auditoria_entidad_fecha ON auditoria_eventos(entidad, entidad_id, creado_en DESC);
