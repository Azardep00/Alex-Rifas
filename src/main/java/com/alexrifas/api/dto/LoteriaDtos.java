package com.alexrifas.api.dto;

import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.model.TipoLoteria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** DTOs del catálogo de loterías y de los resultados cargados a mano (CP-08). */
public final class LoteriaDtos {
    private LoteriaDtos() {}

    static final String URL_HTTP = "^https?://\\S+$";

    /** Alta y edición. En la edición, {@code activa} nulo deja el valor como estaba; en el alta equivale a true. */
    public record LoteriaRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres.") String nombre,
        @NotNull(message = "Indica si es LOTERIA o CHANCE.") TipoLoteria tipo,
        @Size(max = 7, message = "Máximo 7 días.")
        List<@Min(value = 1, message = "Los días van de 1 (lunes) a 7 (domingo).")
             @Max(value = 7, message = "Los días van de 1 (lunes) a 7 (domingo).") Integer> diasSemana,
        LocalTime horaSorteo,
        @Size(max = 300, message = "El enlace no puede superar 300 caracteres.")
        @Pattern(regexp = URL_HTTP, message = "El sitio oficial debe ser un enlace http(s).") String sitioOficialUrl,
        Boolean activa) {}

    public record LoteriaDto(
        Long id, String nombre, TipoLoteria tipo, List<Integer> diasSemana, LocalTime horaSorteo,
        String sitioOficialUrl, boolean activa) {}

    public record CargarResultadoRequest(
        @NotNull(message = "La fecha del sorteo es obligatoria.") LocalDate fechaSorteo,
        @NotBlank(message = "El número mayor es obligatorio.")
        @Pattern(regexp = "^\\d{4}$",
            message = "El número mayor debe tener exactamente 4 dígitos, con ceros a la izquierda si aplica (ej. 0377).")
        String numeroMayor,
        @Pattern(regexp = "^\\d{1,3}$", message = "La serie debe tener de 1 a 3 dígitos.") String serie,
        @Min(value = 1, message = "El número de sorteo debe ser positivo.") Integer numeroSorteo,
        @NotBlank(message = "Indica la fuente del resultado (ej. sitio oficial, transmisión en vivo).")
        @Size(max = 120, message = "La fuente no puede superar 120 caracteres.") String fuente,
        @Size(max = 500, message = "El enlace de evidencia no puede superar 500 caracteres.")
        @Pattern(regexp = URL_HTTP, message = "La evidencia debe ser un enlace http(s).") String evidenciaUrl,
        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres.") String notas) {}

    public record AnularResultadoRequest(
        @NotBlank(message = "El motivo de la anulación es obligatorio.")
        @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres.") String motivo) {}

    /**
     * {@code ultimos2/3/4} muestran de una vez qué boleto gana en rifas de 100, 1000 o 10 000 números, para que
     * quien verifica compare contra la fuente oficial sin hacer cuentas.
     */
    public record ResultadoDto(
        Long id, Long loteriaId, String loteria, String sitioOficialUrl, LocalDate fechaSorteo, String numeroMayor,
        String serie, Integer numeroSorteo, String ultimos2, String ultimos3, String ultimos4, String fuente,
        String evidenciaUrl, String notas, EstadoResultado estado, Long cargadoPor, Instant creadoEn,
        Long verificadoPor, Instant verificadoEn, Long anuladoPor, Instant anuladoEn, String motivoAnulacion) {}
}
