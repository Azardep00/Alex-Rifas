package com.alexrifas.api.security;

import com.alexrifas.api.model.Rol;

/** Lo que el filtro JWT deja en el contexto de seguridad; los controladores lo reciben con @AuthenticationPrincipal. */
public record UsuarioAutenticado(Long id, Rol rol) {
}
