package com.alexrifas.api.service;

import java.util.Locale;

/**
 * Determina la IP del cliente detrás de proxies sin dejar que el cliente la falsifique.
 *
 * <p>Cada proxy de confianza AÑADE al final de X-Forwarded-For la IP desde la que le llegó la conexión.
 * Lo que el cliente escriba queda al principio, así que la IP fiable es la que está a {@code saltos}
 * posiciones desde el final, no la primera. Con {@code saltos = 0} se usa la dirección del socket.
 */
public final class IpCliente {
    private IpCliente() {}

    public static String resolver(String xForwardedFor, String remoteAddr, int saltos) {
        String socket = limpiar(remoteAddr);
        if (saltos <= 0 || xForwardedFor == null || xForwardedFor.isBlank()) {
            return socket;
        }
        String[] partes = xForwardedFor.split(",");
        int indice = Math.max(0, partes.length - saltos);
        String candidata = limpiar(partes[indice]);
        return candidata != null ? candidata : socket;
    }

    /** Acepta solo IPv4/IPv6 en texto (también evita meter basura en las claves de Redis). */
    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim().toLowerCase(Locale.ROOT);
        return v.matches("[0-9a-f:.]{3,45}") ? v : null;
    }
}
