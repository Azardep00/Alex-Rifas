package com.alexrifas.api.util;

import java.util.List;

/**
 * Los números de una rifa van de 0 a (cantidad - 1) y se muestran con ceros a la izquierda
 * (por ejemplo 007 en una rifa de 1000 números), igual que en las rifas con lotería.
 */
public final class FormatoNumero {

    private FormatoNumero() {
    }

    /** Cantidad de dígitos necesarios para escribir el número más alto de una rifa. */
    public static int digitosPara(int cantidadBoletos) {
        if (cantidadBoletos < 1) {
            throw new IllegalArgumentException("La cantidad de boletos debe ser mayor que cero.");
        }
        return String.valueOf(cantidadBoletos - 1).length();
    }

    public static String formatear(int numero, int digitos) {
        if (numero < 0) {
            throw new IllegalArgumentException("El número no puede ser negativo.");
        }
        String texto = String.valueOf(numero);
        if (texto.length() >= digitos) {
            return texto;
        }
        return "0".repeat(digitos - texto.length()) + texto;
    }

    public static List<String> formatear(List<Integer> numeros, int digitos) {
        return numeros.stream().map(n -> formatear(n, digitos)).toList();
    }
}
