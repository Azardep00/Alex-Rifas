package com.alexrifas.api.util;

/**
 * Normaliza teléfonos a solo dígitos para poder compararlos y guardarlos de forma consistente.
 * Reglas:
 *  - Se quitan espacios, guiones, paréntesis y el signo +.
 *  - Un móvil colombiano de 10 dígitos que empieza por 3 se guarda con indicativo: 57XXXXXXXXXX.
 *  - Cualquier otro número debe tener entre 8 y 15 dígitos (formato internacional E.164 sin +).
 */
public final class Telefono {

    private Telefono() {
    }

    /** @throws IllegalArgumentException si el teléfono no tiene un formato válido. */
    public static String normalizar(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new IllegalArgumentException("El teléfono es obligatorio.");
        }
        String limpio = entrada.trim();
        for (int i = 0; i < limpio.length(); i++) {
            char c = limpio.charAt(i);
            boolean permitido = (c >= '0' && c <= '9') || c == '+' || c == ' ' || c == '-' || c == '(' || c == ')';
            if (!permitido) {
                throw new IllegalArgumentException("El teléfono solo puede contener números, espacios, guiones y +.");
            }
        }
        String digitos = limpio.replaceAll("[^0-9]", "");
        if (digitos.length() == 10 && digitos.startsWith("3")) {
            return "57" + digitos;
        }
        if (digitos.length() < 8 || digitos.length() > 15) {
            throw new IllegalArgumentException("El teléfono debe tener entre 8 y 15 dígitos.");
        }
        return digitos;
    }

    /** Muestra solo los últimos 4 dígitos; útil para logs sin datos personales completos. */
    public static String enmascarar(String telefonoNormalizado) {
        if (telefonoNormalizado == null || telefonoNormalizado.length() <= 4) {
            return "****";
        }
        return "*".repeat(telefonoNormalizado.length() - 4)
                + telefonoNormalizado.substring(telefonoNormalizado.length() - 4);
    }
}
