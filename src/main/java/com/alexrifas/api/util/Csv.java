package com.alexrifas.api.util;

import java.util.List;

/**
 * Escritura segura de CSV. Además de escapar comillas, neutraliza la "inyección de fórmulas":
 * si un comprador escribe como nombre =HYPERLINK(...) o +cmd|..., Excel/Sheets lo ejecutaría al
 * abrir el archivo del organizador. Prefijar con una comilla simple lo convierte en texto inofensivo.
 */
public final class Csv {

    private Csv() {
    }

    public static String celda(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString();
        if (!texto.isEmpty()) {
            char primero = texto.charAt(0);
            if (primero == '=' || primero == '+' || primero == '-' || primero == '@'
                    || primero == '\t' || primero == '\r') {
                texto = "'" + texto;
            }
        }
        boolean requiereComillas = texto.contains(",") || texto.contains("\"")
                || texto.contains("\n") || texto.contains("\r");
        if (requiereComillas) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    public static String fila(List<?> valores) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(celda(valores.get(i)));
        }
        return sb.toString();
    }
}
