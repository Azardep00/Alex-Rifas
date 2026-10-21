package com.alexrifas.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration validated at startup; production secrets are supplied through environment variables. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull @Valid Seguridad seguridad) {
    public record Seguridad(
            @NotBlank @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres") String jwtSecret,
            @Min(1) @Max(120) long jwtExpiracionMinutos,
            @Min(1) @Max(90) long refreshExpiracionDias) {
    }
}
