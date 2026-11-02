package com.alexrifas.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Topes antiabuso para reservas sin cuenta (ADR-05). Configurables por entorno:
 * {@code ANTIABUSO_MAX_PENDIENTES_TELEFONO}, {@code ANTIABUSO_MAX_PENDIENTES_IP}, {@code ANTIABUSO_SALTOS_PROXY}.
 *
 * @param maxPendientesPorTelefono reservas PENDIENTES vigentes que puede tener un mismo teléfono (todas las rifas).
 * @param maxPendientesPorIp       reservas PENDIENTES vigentes que puede tener una misma IP. Ojo: las operadoras
 *                                 móviles comparten IP entre muchos usuarios (CGNAT); no lo bajes sin medir.
 * @param saltosProxy              cuántos proxies de confianza hay delante de la API (0 = ninguno). Define qué
 *                                 entrada de X-Forwarded-For se toma como IP real; ver {@code IpCliente}.
 */
@Validated
@ConfigurationProperties(prefix = "app.antiabuso")
public record AntiabusoProperties(
        @DefaultValue("3") @Min(1) @Max(50) int maxPendientesPorTelefono,
        @DefaultValue("5") @Min(1) @Max(500) int maxPendientesPorIp,
        @DefaultValue("1") @Min(0) @Max(5) int saltosProxy) {
}
