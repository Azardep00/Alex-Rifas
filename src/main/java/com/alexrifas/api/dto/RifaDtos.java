package com.alexrifas.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Solicitudes y respuestas de rifas y premios. Las fechas llegan en ISO-8601 con zona, p. ej. 2026-12-01T20:00:00Z. */
public final class RifaDtos {

    private RifaDtos() {
    }

    private static final String HTTPS = "^https://\\S+$";

    public record RifaRequest(
            @NotBlank(message = "El título es obligatorio.")
            @Size(min = 3, max = 120, message = "El título debe tener entre 3 y 120 caracteres.") String titulo,
            @Size(max = 4000, message = "La descripción es demasiado larga.") String descripcion,
            @Size(max = 500) @Pattern(regexp = HTTPS, message = "La imagen debe ser una URL https.") String imagenUrl,
            @NotNull(message = "El precio del boleto es obligatorio.")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero.")
            @Digits(integer = 10, fraction = 2, message = "El precio tiene un formato inválido.") BigDecimal precioBoleto,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "La moneda debe tener 3 letras (por ejemplo COP).") String moneda,
            @NotNull(message = "La cantidad de boletos es obligatoria.")
            @Min(value = 10, message = "La rifa debe tener al menos 10 boletos.") Integer cantidadBoletos,
            @NotNull(message = "La fecha del sorteo es obligatoria.") Instant fechaSorteo,
            @NotNull(message = "La fecha de cierre de ventas es obligatoria.") Instant cierreVentasEn,
            @Min(value = 1, message = "El máximo por compra debe ser al menos 1.") Integer maximoBoletosPorCompra,
            @NotNull(message = "El método de sorteo es obligatorio.") MetodoSorteo metodoSorteo,
            @Size(max = 80) String loteriaNombre,
            @Size(max = 60) String numeroAutorizacion,
            @Size(max = 120) String entidadAutorizadora) {
    }

    public record PremioRequest(
            @NotNull(message = "La posición es obligatoria.")
            @Min(value = 1, message = "La posición mínima es 1.") Integer posicion,
            @NotBlank(message = "El nombre del premio es obligatorio.") @Size(max = 120) String nombre,
            @Size(max = 1000) String descripcion,
            @DecimalMin(value = "0.00", message = "El valor estimado no puede ser negativo.")
            @Digits(integer = 12, fraction = 2, message = "El valor estimado tiene un formato inválido.") BigDecimal valorEstimado,
            @Size(max = 500) @Pattern(regexp = HTTPS, message = "La imagen debe ser una URL https.") String imagenUrl) {
    }

    public record PremioDto(Long id, int posicion, String nombre, String descripcion,
                            BigDecimal valorEstimado, String imagenUrl) {
    }

    public record Disponibilidad(long disponibles, long reservados, long vendidos) {
    }

    public record RifaResumenDto(
            String codigo,
            String titulo,
            String imagenUrl,
            BigDecimal precioBoleto,
            String moneda,
            int cantidadBoletos,
            long boletosDisponibles,
            EstadoRifa estado,
            Instant fechaSorteo,
            Instant cierreVentasEn) {
    }

    public record RifaDetalleDto(
            String codigo,
            String titulo,
            String descripcion,
            String imagenUrl,
            BigDecimal precioBoleto,
            String moneda,
            int cantidadBoletos,
            /** Dígitos con los que se escriben los números (con ceros a la izquierda). */
            int digitosNumero,
            EstadoRifa estado,
            Instant fechaSorteo,
            Instant cierreVentasEn,
            int maximoBoletosPorCompra,
            MetodoSorteo metodoSorteo,
            String loteriaNombre,
            String numeroAutorizacion,
            String entidadAutorizadora,
            Instant publicadaEn,
            String organizador,
            Disponibilidad disponibilidad,
            List<PremioDto> premios) {
    }

    public record PaginaDto<T>(List<T> contenido, int pagina, int tamano, long total, int totalPaginas) {
    }
}
