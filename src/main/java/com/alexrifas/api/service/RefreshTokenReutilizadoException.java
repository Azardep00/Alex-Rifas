package com.alexrifas.api.service;

/**
 * Se presentó un refresh token que ya había sido usado o revocado. Puede indicar que fue robado,
 * así que el servicio cierra todas las sesiones de ese usuario antes de lanzar esta excepción.
 */
public class RefreshTokenReutilizadoException extends RuntimeException {
    public RefreshTokenReutilizadoException() {
        super("La sesión ya no es válida. Inicia sesión de nuevo.");
    }
}
