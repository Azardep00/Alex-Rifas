package com.alexrifas.api.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Normaliza celulares colombianos a +57XXXXXXXXXX para guardar y consultar siempre igual. */
public final class TelefonoColombia {
    private TelefonoColombia() {}

    public static String normalizar(String entrada) {
        String digitos = entrada == null ? "" : entrada.replaceAll("[\\s\\-().+]", "");
        if (digitos.startsWith("57") && digitos.length() == 12) {
            digitos = digitos.substring(2);
        }
        if (!digitos.matches("3\\d{9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Escribe un celular colombiano válido de 10 dígitos (ej. 300 123 4567).");
        }
        return "+57" + digitos;
    }

    /** +573001234567 → +57 300 ••• 4567 */
    public static String enmascarar(String telefono) {
        if (telefono == null || telefono.length() < 13) {
            return null;
        }
        return telefono.substring(0, 3) + " " + telefono.substring(3, 6) + " ••• " + telefono.substring(9);
    }
}
