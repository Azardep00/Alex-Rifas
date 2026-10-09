package com.alexrifas.api.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Un solo lugar para traducir excepciones a respuestas HTTP con el formato {@link ErrorResponse}.
 * Regla de seguridad: al cliente NUNCA se le devuelven detalles internos (stack traces, nombres de
 * tablas, mensajes de SQL). Esos detalles van al log del servidor con el mismo rastreoId.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> manejarApi(ApiException ex) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(ex.getStatus());
        if (ex.getReintentarEnSegundos() != null) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getReintentarEnSegundos()));
        }
        return respuesta.body(new ErrorResponse(
                ex.getCodigo(), ex.getMessage(), null, ex.getDatos(), rastreoId(), Instant.now()));
    }

    // Se lanza desde Utilidades (Telefono, PoliticaContrasena...) con mensajes pensados para el usuario.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarArgumentoInvalido(IllegalArgumentException ex) {
        return construir(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDACION", "Hay datos inválidos en la solicitud.", errores, null, rastreoId(), Instant.now()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestricciones(ConstraintViolationException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errores.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDACION", "Hay datos inválidos en la solicitud.", errores, null, rastreoId(), Instant.now()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionMetodo(HandlerMethodValidationException ex) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay parámetros inválidos en la solicitud.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return construir(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "El cuerpo de la petición es inválido o no tiene el formato esperado.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoParametro(MethodArgumentTypeMismatchException ex) {
        return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "El parámetro '" + ex.getName() + "' no es válido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(MissingServletRequestParameterException ex) {
        return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método HTTP no permitido en esta ruta.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarTipoMedio(HttpMediaTypeNotSupportedException ex) {
        return construir(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO", "Usa Content-Type: application/json.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarRutaInexistente(NoResourceFoundException ex) {
        return construir(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Recurso no encontrado.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex) {
        return construir(HttpStatus.FORBIDDEN, "PROHIBIDO", "No tienes permiso para realizar esta acción.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarAutenticacion(AuthenticationException ex) {
        return construir(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Debes iniciar sesión para acceder a este recurso.");
    }

    // Dos personas modificaron la misma fila a la vez (@Version). Se pide reintentar.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> manejarConcurrencia(ObjectOptimisticLockingFailureException ex) {
        return construir(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El recurso cambió mientras lo editabas. Vuelve a intentarlo.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> manejarBloqueo(PessimisticLockingFailureException ex) {
        log.warn("No se pudo obtener un bloqueo de fila a tiempo [{}]: {}", rastreoId(), ex.getClass().getSimpleName());
        return construir(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_OCUPADO",
                "El sistema está muy ocupado en este momento. Intenta de nuevo en unos segundos.");
    }

    // Violación de restricciones de la BD (por ejemplo un UNIQUE). Sin filtrar el nombre de la restricción.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos [{}]: {}", rastreoId(), ex.getMostSpecificCause().getClass().getSimpleName());
        return construir(HttpStatus.CONFLICT, "CONFLICTO_DATOS",
                "La operación entra en conflicto con datos existentes.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex) {
        log.error("Error inesperado [{}]", rastreoId(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Si persiste, comunica este código de rastreo a soporte.");
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(ErrorResponse.de(codigo, mensaje, rastreoId()));
    }

    private static String rastreoId() {
        return MDC.get("rastreoId");
    }
}
