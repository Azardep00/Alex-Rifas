package com.alexrifas.api.model;

/** Estados de una transacción tal como los reporta Wompi. */
public enum EstadoPagoWompi {
    PENDING,
    APPROVED,
    DECLINED,
    VOIDED,
    ERROR
}
