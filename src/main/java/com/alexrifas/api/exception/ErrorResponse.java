package com.alexrifas.api.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato único de error de toda la API. Mantiene el campo "mensaje" que ya usaban los frontends
 * del proyecto anterior y agrega un código estable, errores por campo y un id de rastreo
 * (el mismo que aparece en los logs del servidor) para soporte.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String codigo,
        String mensaje,
        Map<String, String> errores,
        Map<String, Object> datos,
        String rastreoId,
        Instant fecha) {

    public static ErrorResponse de(String codigo, String mensaje, String rastreoId) {
        return new ErrorResponse(codigo, mensaje, null, null, rastreoId, Instant.now());
    }
}
