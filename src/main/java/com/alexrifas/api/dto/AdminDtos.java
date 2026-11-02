package com.alexrifas.api.dto;

import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.model.EstadoRifa;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTOs de la administración de compras y del resumen de rifa (CP-06). */
public final class AdminDtos {
    private AdminDtos() {}

    /**
     * Fila de la cola de pagos. El administrador ve el teléfono completo (lo necesita para contactar al
     * comprador); en compras con cuenta, nombre y teléfono son nulos y viene {@code usuarioId}.
     */
    public record CompraAdminDto(
        String codigo, String codigoRifa, EstadoCompra estado, String compradorNombre, String compradorTelefono,
        Long usuarioId, List<Integer> numeros, BigDecimal subtotal, String moneda,
        Instant expiraEn, Instant creadaEn, Instant actualizadaEn) {}

    public record LiberacionRequest(
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres.") String motivo) {}

    public record LiberacionResponse(String codigo, EstadoCompra estado, int boletosLiberados, Instant liberadaEn) {}

    /**
     * Resumen para el panel. {@code reservados} cuenta solo reservas vigentes (una vencida aún no procesada
     * por el job cuenta como disponible, igual que la grilla). {@code recaudoPendiente} es el dinero de
     * reservas vigentes que todavía no se han pagado; no es ingreso. Los contadores de compras son por estado
     * tal como están guardados.
     */
    public record ResumenRifa(
        String codigo, String titulo, EstadoRifa estado, String moneda, BigDecimal precioBoleto,
        int cantidadBoletos, long disponibles, long reservados, long vendidos, BigDecimal porcentajeVendido,
        BigDecimal recaudoConfirmado, BigDecimal recaudoPendiente, BigDecimal recaudoMaximo,
        long comprasPendientes, long comprasConfirmadas, long comprasExpiradas, long comprasCanceladas,
        Instant cierreVentasEn, Instant fechaSorteo) {}
}
