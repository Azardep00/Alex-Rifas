package com.alexrifas.api.model;

/** Qué pasó con un resultado de lotería al aplicarlo a un premio (CP-09). */
public enum ResolucionPaso {
    /** El número de la lotería correspondía a un boleto vendido: ese boleto gana. */
    DIRECTO,
    /** El cupo estaba vacío y, con ADJUDICAR_SIGUIENTE_VENDIDO, ganó el siguiente número vendido. */
    ADJUDICADO_SIGUIENTE_VENDIDO,
    /** El cupo estaba vacío y, con REPETIR_SIGUIENTE_LOTERIA, este resultado no decide el premio: se pasa al siguiente. */
    REPETIDO
}
