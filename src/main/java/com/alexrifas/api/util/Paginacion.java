package com.alexrifas.api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Paginación con límites: nadie puede pedir una página de un millón de filas. */
public final class Paginacion {

    public static final int TAMANO_MAXIMO = 100;

    private Paginacion() {
    }

    public static Pageable de(int pagina, int tamano) {
        int p = Math.max(0, pagina);
        int t = Math.min(Math.max(1, tamano), TAMANO_MAXIMO);
        return PageRequest.of(p, t);
    }
}
