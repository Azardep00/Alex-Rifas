package com.alexrifas.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SorteoDtos {
    private SorteoDtos() {}

    public record SorteoRequest(
        @Size(max = 100, message = "La lista de ganadores es demasiado larga.")
        List<@NotNull @Min(value = 0, message = "El número ganador no puede ser negativo.") Integer> numerosGanadores) {}

    public record GanadorDto(String codigoRifa, String tituloRifa, int posicionPremio, String premio,
                             int numeroBoleto, boolean publicado, Instant creadoEn) {}

    // ---- sorteo por lotería (CP-09) ----

    /**
     * Un resultado de lotería aplicado a un premio. Los números van como texto con ceros a la izquierda (ADR-06).
     * {@code numeroBoleto} es nulo cuando {@code resolucion} es REPETIDO; {@code causa} es nulo cuando es DIRECTO.
     */
    public record PasoLoteriaDto(int posicionPremio, Long resultadoId, LocalDate fechaSorteoLoteria, String numeroMayor,
                                 String numeroObjetivo, String resolucion, String causa, String numeroBoleto) {}

    public record GanadorLoteriaDto(int posicionPremio, String premio, int numeroBoleto, String numeroBoletoTexto,
                                    Long resultadoId, String numeroObjetivo, boolean adjudicadoPorVacante) {}

    /**
     * Resultado de previsualizar o ejecutar un sorteo por lotería. {@code completo} es falso si aún falta (o está
     * sin verificar) un resultado; {@code mensaje} dice qué hacer. {@code ejecutado} solo es verdadero cuando la
     * rifa quedó SORTEADA. {@code ganadores} puede venir parcial en una previsualización incompleta.
     */
    public record SorteoLoteriaDto(String codigoRifa, String tituloRifa, String loteria, int digitos,
                                   String reglaVacante, boolean completo, boolean ejecutado, String mensaje,
                                   List<PasoLoteriaDto> pasos, List<GanadorLoteriaDto> ganadores) {}
}
