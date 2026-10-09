package com.alexrifas.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Toda la configuración propia de la aplicación en un solo lugar y validada al arrancar.
 * Si algo está mal (por ejemplo un JWT_SECRET corto) la app no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @DefaultValue Seguridad seguridad,
        @Valid @DefaultValue Cors cors,
        @Valid @DefaultValue Rifas rifas,
        @Valid @DefaultValue Wompi wompi,
        @Valid @DefaultValue Admin admin,
        @Valid @DefaultValue Limites limites,
        @Valid @DefaultValue Scheduler scheduler) {

    public record Seguridad(
            @NotBlank @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres") String jwtSecret,
            @DefaultValue("15") @Min(1) long jwtExpiracionMinutos,
            @DefaultValue("30") @Min(1) long refreshExpiracionDias,
            @DefaultValue("12") @Min(4) @Max(16) int bcryptCosto,
            String csp) {
    }

    public record Cors(List<String> origenesPermitidos) {
        public Cors {
            origenesPermitidos = origenesPermitidos == null
                    ? List.of()
                    : origenesPermitidos.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Rifas(
            @DefaultValue("10000") @Min(10) int maxBoletos,
            @DefaultValue("15") @Min(1) int minutosReserva,
            @DefaultValue("20") @Min(1) int minutosExtensionPago,
            @DefaultValue("3") @Min(1) int maxReservasPendientesPorTelefono,
            @DefaultValue("100") @Min(1) int maxBoletosPorCompraTope,
            @DefaultValue("10") @Min(1) int maxBoletosPorCompraDefecto,
            @DefaultValue("20") @Min(1) int maxPremios,
            @DefaultValue("true") boolean exigirAutorizacion,
            @DefaultValue("18") @Min(1) int edadMinima,
            @DefaultValue("false") boolean permitirSorteoAnticipado) {
    }

    public record Wompi(
            @DefaultValue("false") boolean habilitado,
            @DefaultValue("https://sandbox.wompi.co") String apiUrl,
            String llavePublica,
            String llavePrivada,
            String secretoIntegridad,
            String secretoEventos) {

        /** Wompi solo se considera operativo si está habilitado Y tiene todas sus llaves. */
        public boolean configurado() {
            return habilitado
                    && notBlank(llavePublica)
                    && notBlank(llavePrivada)
                    && notBlank(secretoIntegridad)
                    && notBlank(secretoEventos);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public record Admin(String correo, String contrasena, @DefaultValue("Administrador") String nombre) {
    }

    public record Limites(
            @DefaultValue("true") boolean habilitado,
            @DefaultValue("10") @Min(1) int registroPor15Min,
            @DefaultValue("12") @Min(1) int reservasPor10Min,
            @DefaultValue("40") @Min(1) int consultasPor5Min,
            @DefaultValue("30") @Min(1) int refreshPor5Min,
            @DefaultValue("65536") @Min(1024) long maxCuerpoBytes) {
    }

    public record Scheduler(
            @DefaultValue("true") boolean habilitado,
            @DefaultValue("60000") @Min(1000) long intervaloExpiracionMs) {
    }
}
