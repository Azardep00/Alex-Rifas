package com.alexrifas.api.model;

public enum MetodoSorteo {
    /**
     * El ganador lo define el resultado de una lotería oficial (p. ej. las últimas cifras).
     * El organizador registra el número ganador y la evidencia (lotería, fecha, resultado).
     * Es el método más transparente porque la fuente es externa y pública.
     */
    LOTERIA,
    /** El sistema elige al azar (SecureRandom) entre los boletos vendidos. */
    ALEATORIO
}
