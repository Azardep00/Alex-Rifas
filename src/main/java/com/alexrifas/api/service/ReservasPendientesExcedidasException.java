package com.alexrifas.api.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429: se alcanzó el tope de reservas pendientes. Lleva los segundos para la cabecera Retry-After. */
public class ReservasPendientesExcedidasException extends ResponseStatusException {
    private final long retryAfterSegundos;

    public ReservasPendientesExcedidasException(String mensaje, long retryAfterSegundos) {
        super(HttpStatus.TOO_MANY_REQUESTS, mensaje);
        this.retryAfterSegundos = Math.max(1, retryAfterSegundos);
    }

    public long getRetryAfterSegundos() {
        return retryAfterSegundos;
    }
}
