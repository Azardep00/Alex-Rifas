package com.alexrifas.api.dto;

import com.alexrifas.api.model.EstadoCompra;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CompraDtos {
    private CompraDtos() {}

    public record ReservaRequest(
        @NotBlank(message = "El código de la rifa es obligatorio.") String codigoRifa,
        @NotEmpty(message = "Selecciona al menos un número.")
        @Size(max = 100, message = "No puedes reservar más de 100 números en una solicitud.")
        List<@NotNull @Min(value = 0, message = "El número del boleto no puede ser negativo.") Integer> numeros) {}

    /** Reserva de invitado (sin cuenta): nombre + teléfono + números. */
    public record ReservaPublicaRequest(
        @NotBlank(message = "El código de la rifa es obligatorio.") String codigoRifa,
        @NotBlank(message = "Escribe tu nombre.")
        @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.") String nombre,
        @NotBlank(message = "Escribe tu teléfono.")
        @Size(max = 30, message = "El teléfono es demasiado largo.") String telefono,
        @NotEmpty(message = "Selecciona al menos un número.")
        @Size(max = 100, message = "No puedes reservar más de 100 números en una solicitud.")
        List<@NotNull @Min(value = 0, message = "El número del boleto no puede ser negativo.") Integer> numeros) {}

    /** Lo que ve el invitado: no expone ids internos ni el teléfono completo. */
    public record ReservaPublicaResponse(
        String codigo, String codigoRifa, EstadoCompra estado, List<Integer> numeros,
        BigDecimal subtotal, String moneda, String compradorNombre, String telefonoEnmascarado,
        Instant expiraEn, Instant creadaEn) {}

    public record ConfirmacionManualRequest(
        @NotBlank(message = "Indica el método o soporte del pago validado.")
        @Size(max = 80) String metodo,
        @Size(max = 500) String observacion) {}

    public record CompraResponse(
        String codigo, String codigoRifa, EstadoCompra estado, List<Integer> numeros,
        BigDecimal subtotal, String moneda, Instant expiraEn, Instant creadaEn,
        Instant actualizadaEn) {}

    public record ConfirmacionResponse(String codigoCompra, EstadoCompra estado, String referenciaPago,
                                       Instant confirmadoEn) {}
}
