package com.alexrifas.api.util;

import java.security.SecureRandom;

/**
 * Genera códigos públicos aleatorios (para el link de una rifa o el comprobante de una compra).
 * Usa el alfabeto de Crockford (sin I, L, O, U) para evitar confusiones al leerlos o dictarlos,
 * y SecureRandom para que no sean predecibles (nunca se usan ids secuenciales en rutas públicas).
 */
public final class CodigoPublico {

    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoPublico() {
    }

    public static String generar(int longitud) {
        if (longitud < 6 || longitud > 16) {
            throw new IllegalArgumentException("La longitud del código debe estar entre 6 y 16.");
        }
        char[] resultado = new char[longitud];
        for (int i = 0; i < longitud; i++) {
            resultado[i] = ALFABETO[RANDOM.nextInt(ALFABETO.length)];
        }
        return new String(resultado);
    }
}
