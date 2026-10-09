package com.alexrifas.api.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas. Mínimo 8 caracteres, al menos una letra y un número, y máximo 72 bytes
 * (límite real de BCrypt: lo que pase de ahí se ignoraría en silencio).
 */
public final class PoliticaContrasena {

    public static final int MINIMO = 8;
    public static final int MAXIMO_BYTES = 72;

    private static final Set<String> COMUNES = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "password123",
            "qwerty123", "contrasena1", "contraseña1", "abc12345", "admin123", "11111111");

    private PoliticaContrasena() {
    }

    /** @throws IllegalArgumentException con un mensaje apto para mostrar al usuario. */
    public static void validar(String contrasena) {
        if (contrasena == null || contrasena.length() < MINIMO) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MINIMO + " caracteres.");
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new IllegalArgumentException("La contraseña es demasiado larga.");
        }
        boolean hayLetra = false;
        boolean hayNumero = false;
        for (int i = 0; i < contrasena.length(); i++) {
            char c = contrasena.charAt(i);
            if (Character.isLetter(c)) {
                hayLetra = true;
            } else if (Character.isDigit(c)) {
                hayNumero = true;
            }
        }
        if (!hayLetra || !hayNumero) {
            throw new IllegalArgumentException("La contraseña debe incluir al menos una letra y un número.");
        }
        if (COMUNES.contains(contrasena.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Esa contraseña es demasiado común. Elige otra.");
        }
    }
}
