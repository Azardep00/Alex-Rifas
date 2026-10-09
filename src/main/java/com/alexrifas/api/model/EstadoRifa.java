package com.alexrifas.api.model;

public enum EstadoRifa {
    /** En preparación: aún no es visible ni vende boletos. */
    BORRADOR,
    /** Publicada y vendiendo boletos. */
    ACTIVA,
    /** Ventas cerradas, a la espera del sorteo. */
    CERRADA,
    /** Todos los premios ya tienen ganador. */
    SORTEADA,
    /** Cancelada por el organizador (las compras pagadas pasan a reembolso). */
    CANCELADA
}
