package com.alexrifas.api.dto;

import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RifaDtos {
    private RifaDtos() {}

    /** Cuerpo para crear una rifa (queda en BORRADOR). El orden de los campos es parte del contrato. */
    public record RifaRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 120, message = "El título no puede superar 120 caracteres.") String titulo,
        @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres.") String descripcion,
        @Size(max = 500, message = "La URL de la imagen no puede superar 500 caracteres.") String imagenUrl,
        @NotNull(message = "El precio del boleto es obligatorio.")
        @DecimalMin(value = "0.01", message = "El precio del boleto debe ser mayor que cero.")
        @Digits(integer = 10, fraction = 2, message = "El precio del boleto tiene un formato inválido.") BigDecimal precioBoleto,
        @NotBlank(message = "La moneda es obligatoria.")
        @Size(min = 3, max = 3, message = "La moneda debe tener 3 letras (por ejemplo COP).") String moneda,
        @NotNull(message = "La cantidad de boletos es obligatoria.")
        @Min(value = 10, message = "La rifa debe tener al menos 10 boletos.")
        @Max(value = 50000, message = "La rifa no puede tener más de 50.000 boletos.") Integer cantidadBoletos,
        @NotNull(message = "La fecha del sorteo es obligatoria.") Instant fechaSorteo,
        @NotNull(message = "La fecha de cierre de ventas es obligatoria.") Instant cierreVentasEn,
        @NotNull(message = "El máximo de boletos por compra es obligatorio.")
        @Min(value = 1, message = "El máximo de boletos por compra debe ser al menos 1.") Integer maximoBoletosPorCompra,
        @NotNull(message = "El método de sorteo es obligatorio.") MetodoSorteo metodoSorteo,
        @Size(max = 80, message = "El nombre de la lotería no puede superar 80 caracteres.") String loteriaNombre,
        @Size(max = 60, message = "El número de autorización no puede superar 60 caracteres.") String numeroAutorizacion,
        @Size(max = 120, message = "La entidad autorizadora no puede superar 120 caracteres.") String entidadAutorizadora) {}

    public record PremioRequest(
        @NotNull(message = "La posición del premio es obligatoria.")
        @Min(value = 1, message = "La posición del premio debe ser al menos 1.")
        @Max(value = 100, message = "La posición del premio no puede superar 100.") Integer posicion,
        @NotBlank(message = "El nombre del premio es obligatorio.")
        @Size(max = 120, message = "El nombre del premio no puede superar 120 caracteres.") String nombre,
        @Size(max = 1000, message = "La descripción del premio no puede superar 1000 caracteres.") String descripcion,
        @DecimalMin(value = "0.00", message = "El valor estimado no puede ser negativo.")
        @Digits(integer = 12, fraction = 2, message = "El valor estimado tiene un formato inválido.") BigDecimal valorEstimado,
        @Size(max = 500, message = "La URL de la imagen no puede superar 500 caracteres.") String imagenUrl) {}

    public record PremioDto(int posicion, String nombre, String descripcion, BigDecimal valorEstimado,
                            String imagenUrl) {}

    public record Disponibilidad(long disponibles, long reservados, long vendidos) {}

    /** Tarjeta del catálogo público (la usa el sitio web Flask). */
    public record RifaResumen(String codigo, String titulo, String imagenUrl, EstadoRifa estado,
                              BigDecimal precioBoleto, String moneda, int cantidadBoletos,
                              long boletosDisponibles, Instant fechaSorteo) {}

    /** Detalle público de una rifa (la usa el sitio web Flask). */
    public record RifaDetalle(String codigo, String titulo, String descripcion, String imagenUrl,
                              EstadoRifa estado, BigDecimal precioBoleto, String moneda, int cantidadBoletos,
                              Instant fechaSorteo, Instant cierreVentasEn, int maximoBoletosPorCompra,
                              MetodoSorteo metodoSorteo, String loteriaNombre, String organizador,
                              Disponibilidad disponibilidad, List<PremioDto> premios) {}

    /** Vista para organizadores y administradores: incluye el id interno que usan los endpoints de gestión. */
    public record RifaGestionDto(Long id, String codigo, String titulo, EstadoRifa estado,
                                 BigDecimal precioBoleto, String moneda, int cantidadBoletos,
                                 Instant fechaSorteo, Instant cierreVentasEn, int maximoBoletosPorCompra,
                                 MetodoSorteo metodoSorteo, String numeroAutorizacion,
                                 String entidadAutorizadora, Disponibilidad disponibilidad) {}

    public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {}
}
