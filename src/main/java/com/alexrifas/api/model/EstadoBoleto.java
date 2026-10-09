package com.alexrifas.api.model;

public enum EstadoBoleto {
    DISPONIBLE,
    /** Apartado por una compra pendiente de pago, hasta {@code reservadoHasta}. */
    RESERVADO,
    VENDIDO
}
