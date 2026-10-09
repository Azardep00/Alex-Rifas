package com.alexrifas.api.model;

public enum EstadoCompra {
    /** Números apartados, esperando el pago antes de que venza la reserva. */
    PENDIENTE_PAGO,
    /** Pago confirmado: los números son del comprador. */
    PAGADA,
    /** La reserva venció sin pago; los números se liberaron. */
    EXPIRADA,
    /** Cancelada por el comprador o el organizador antes de pagar. */
    CANCELADA,
    /** Hubo pago pero no se pueden entregar los números (o se canceló): hay que devolver el dinero. */
    REEMBOLSO_PENDIENTE,
    /** El dinero ya fue devuelto al comprador. */
    REEMBOLSADA
}
