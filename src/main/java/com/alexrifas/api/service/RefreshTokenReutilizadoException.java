package com.alexrifas.api.service;

/** Internal marker: the service commits revocation of the user's refresh sessions before returning 401. */
public class RefreshTokenReutilizadoException extends RuntimeException {
    public RefreshTokenReutilizadoException() {
        super("La sesión ha expirado o ya no es válida. Inicia sesión nuevamente.");
    }
}
