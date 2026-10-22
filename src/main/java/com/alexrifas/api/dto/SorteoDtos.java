package com.alexrifas.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SorteoDtos {
    private SorteoDtos() {}

    public record SorteoRequest(
        @Size(max = 100, message = "La lista de ganadores es demasiado larga.")
        List<@NotNull @Min(value = 0, message = "El número ganador no puede ser negativo.") Integer> numerosGanadores) {}

    public record GanadorDto(String codigoRifa, String tituloRifa, int posicionPremio, String premio,
                             int numeroBoleto, boolean publicado, Instant creadoEn) {}
}
