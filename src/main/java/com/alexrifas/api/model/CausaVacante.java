package com.alexrifas.api.model;

/** Por qué el número de la lotería no pudo adjudicarse directamente (CP-09). */
public enum CausaVacante {
    /** El boleto con ese número no se vendió. */
    NO_VENDIDO,
    /** El boleto con ese número ya ganó otro premio de la misma rifa (un boleto no puede ganar dos premios). */
    YA_GANO_OTRO_PREMIO
}
