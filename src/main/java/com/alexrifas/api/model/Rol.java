package com.alexrifas.api.model;

public enum Rol {
    /** Compra boletos y ve su historial. Se registra solo. */
    CLIENTE,
    /** Crea y administra sus propias rifas. Lo crea un ADMIN. */
    ORGANIZADOR,
    /** Administra la plataforma: crea organizadores, ve la auditoría. */
    ADMIN
}
