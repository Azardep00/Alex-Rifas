package com.alexrifas.api.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con código HTTP y un código estable de máquina (por ejemplo BOLETOS_NO_DISPONIBLES)
 * para que el frontend pueda reaccionar sin depender del texto del mensaje.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final Map<String, Object> datos;
    private final Long reintentarEnSegundos;

    private ApiException(HttpStatus status, String codigo, String mensaje,
                         Map<String, Object> datos, Long reintentarEnSegundos) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.datos = datos;
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }

    public Long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }

    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje, null, null);
    }

    public static ApiException solicitudInvalida(String codigo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, mensaje, null, null);
    }

    public static ApiException conflicto(String codigo, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje, null, null);
    }

    public static ApiException conflicto(String codigo, String mensaje, Map<String, Object> datos) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje, datos, null);
    }

    public static ApiException noAutenticado(String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", mensaje, null, null);
    }

    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, "PROHIBIDO", mensaje, null, null);
    }

    public static ApiException demasiadosIntentos(String mensaje, long reintentarEnSegundos) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", mensaje, null,
                reintentarEnSegundos);
    }

    public static ApiException servicioNoDisponible(String codigo, String mensaje) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje, null, null);
    }
}
