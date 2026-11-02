package com.alexrifas.api.service;

import com.alexrifas.api.model.Rol;
import com.alexrifas.api.security.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Defensa en profundidad: además de la regla de {@code SecurityConfig}, cada operación vuelve a exigir ADMIN. */
final class SoloAdmin {
    private SoloAdmin() {}

    static void exigir(UsuarioAutenticado actor) {
        if (actor == null || actor.rol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede hacer esta operación.");
        }
    }
}
