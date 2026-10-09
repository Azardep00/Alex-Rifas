package com.alexrifas.api.util;

/** Utilidades de texto para no exponer datos personales completos en respuestas públicas ni logs. */
public final class Textos {

    private Textos() {
    }

    /** "Juan Carlos Pérez" -> "Juan P." ; "Ana" -> "Ana". Se usa en la lista pública de ganadores. */
    public static String enmascararNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "";
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        String primero = partes[0];
        if (partes.length == 1) {
            return primero;
        }
        String ultimo = partes[partes.length - 1];
        return primero + " " + ultimo.charAt(0) + ".";
    }

    /** Recorta a un máximo de caracteres (null-safe). */
    public static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    /** Devuelve null si el texto es null o está en blanco; si no, el texto sin espacios sobrantes. */
    public static String limpiarONull(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}
