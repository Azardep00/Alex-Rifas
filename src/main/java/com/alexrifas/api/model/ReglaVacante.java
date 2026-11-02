package com.alexrifas.api.model;

/**
 * Qué hacer cuando el número que sale en la lotería cae en un boleto que no se vendió (ADR-07).
 * Debe publicarse de cara al comprador antes de empezar a vender.
 */
public enum ReglaVacante {
    /** El sorteo se repite con el resultado de la siguiente lotería. */
    REPETIR_SIGUIENTE_LOTERIA,
    /** Se adjudica al siguiente número vendido (en orden ascendente, con vuelta al inicio). */
    ADJUDICAR_SIGUIENTE_VENDIDO
}
