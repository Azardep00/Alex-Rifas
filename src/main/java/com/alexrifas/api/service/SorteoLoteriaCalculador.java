package com.alexrifas.api.service;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.CausaVacante;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.model.ReglaVacante;
import com.alexrifas.api.model.ResolucionPaso;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reglas puras del sorteo por lotería (CP-09): sin base de datos ni reloj, para poder probar cada regla sola.
 *
 * <p>Es determinista: con los mismos resultados verificados, boletos vendidos y premios, da siempre el mismo
 * ganador. Por eso volver a ejecutar tras cargar el siguiente resultado no cambia lo ya decidido, y nadie puede
 * "escoger" el resultado que le conviene: los resultados se consumen en orden de fecha, uno por intento.
 *
 * <ul>
 *   <li>Ganador = últimos N dígitos del número mayor ({@code ResultadoLoteria#ultimosDigitos}), con ceros a la izquierda.</li>
 *   <li>Cada premio, en orden de posición, consume el siguiente resultado de la cadena.</li>
 *   <li>Si ese boleto no se vendió (o ya ganó otro premio): {@code ADJUDICAR_SIGUIENTE_VENDIDO} da el premio al
 *       siguiente número vendido en orden ascendente (con vuelta al inicio); {@code REPETIR_SIGUIENTE_LOTERIA}
 *       descarta ese resultado y el premio espera al siguiente.</li>
 *   <li>Si el siguiente resultado de la cadena aún está sin verificar, el cálculo se detiene: no se salta.</li>
 * </ul>
 */
final class SorteoLoteriaCalculador {
    private SorteoLoteriaCalculador() {}

    enum Bloqueo {
        /** Todos los premios quedaron asignados. */
        NINGUNO,
        /** Se acabaron los resultados verificados y aún faltan premios por decidir. */
        FALTA_RESULTADO,
        /** El siguiente resultado de la cadena sigue PENDIENTE_VERIFICACION. */
        RESULTADO_PENDIENTE
    }

    /** Un resultado aplicado a un premio. {@code boleto} es nulo cuando la resolución es REPETIDO. */
    record Paso(int posicionPremio, ResultadoLoteria resultado, int numeroObjetivo, ResolucionPaso resolucion,
                CausaVacante causa, Boleto boleto) {}

    record Asignacion(Premio premio, Boleto boleto, ResultadoLoteria resultado, int numeroObjetivo,
                      boolean porVacante) {}

    /**
     * @param premioBloqueado posición del premio que no pudo decidirse (solo si {@code bloqueo != NINGUNO})
     * @param pendiente       el resultado sin verificar que detuvo el cálculo (solo con RESULTADO_PENDIENTE)
     */
    record Plan(List<Paso> pasos, List<Asignacion> asignaciones, Bloqueo bloqueo, Integer premioBloqueado,
                ResultadoLoteria pendiente) {
        boolean completo() { return bloqueo == Bloqueo.NINGUNO; }
    }

    /**
     * @param premios  premios de la rifa en orden de posición ascendente
     * @param digitos  2, 3 o 4 (la rifa tiene exactamente 10^digitos boletos numerados 0..10^digitos-1)
     * @param vendidos boletos VENDIDO de la rifa (en cualquier orden)
     * @param cadena   resultados no anulados de la lotería de la rifa desde la fecha del sorteo, por fecha ascendente
     */
    static Plan calcular(List<Premio> premios, int digitos, ReglaVacante regla, List<Boleto> vendidos,
                         List<ResultadoLoteria> cadena) {
        if (digitos < 2 || digitos > 4) {
            throw new IllegalArgumentException("Solo se admiten los últimos 2, 3 o 4 dígitos.");
        }
        NavigableMap<Integer, Boleto> vendidosPorNumero = new TreeMap<>();
        vendidos.forEach(b -> vendidosPorNumero.put(b.getNumero(), b));
        List<ResultadoLoteria> resultados = cadena.stream().filter(r -> r.getEstado() != EstadoResultado.ANULADO).toList();

        Set<Integer> tomados = new HashSet<>();
        List<Paso> pasos = new ArrayList<>();
        List<Asignacion> asignaciones = new ArrayList<>();
        int siguiente = 0;

        for (Premio premio : premios) {
            boolean decidido = false;
            while (!decidido) {
                if (siguiente >= resultados.size()) {
                    return new Plan(pasos, asignaciones, Bloqueo.FALTA_RESULTADO, premio.getPosicion(), null);
                }
                ResultadoLoteria resultado = resultados.get(siguiente);
                if (resultado.getEstado() != EstadoResultado.VERIFICADO) {
                    return new Plan(pasos, asignaciones, Bloqueo.RESULTADO_PENDIENTE, premio.getPosicion(), resultado);
                }
                siguiente++;

                int objetivo = Integer.parseInt(resultado.ultimosDigitos(digitos));
                Boleto boleto = vendidosPorNumero.get(objetivo);
                CausaVacante causa = boleto == null ? CausaVacante.NO_VENDIDO
                    : tomados.contains(objetivo) ? CausaVacante.YA_GANO_OTRO_PREMIO : null;

                if (causa == null) {
                    tomados.add(objetivo);
                    pasos.add(new Paso(premio.getPosicion(), resultado, objetivo, ResolucionPaso.DIRECTO, null, boleto));
                    asignaciones.add(new Asignacion(premio, boleto, resultado, objetivo, false));
                    decidido = true;
                } else if (regla == ReglaVacante.ADJUDICAR_SIGUIENTE_VENDIDO) {
                    Boleto elegido = siguienteVendidoLibre(vendidosPorNumero, tomados, objetivo);
                    tomados.add(elegido.getNumero());
                    pasos.add(new Paso(premio.getPosicion(), resultado, objetivo,
                        ResolucionPaso.ADJUDICADO_SIGUIENTE_VENDIDO, causa, elegido));
                    asignaciones.add(new Asignacion(premio, elegido, resultado, objetivo, true));
                    decidido = true;
                } else {
                    pasos.add(new Paso(premio.getPosicion(), resultado, objetivo, ResolucionPaso.REPETIDO, causa, null));
                }
            }
        }
        return new Plan(pasos, asignaciones, Bloqueo.NINGUNO, null, null);
    }

    /** Siguiente número vendido y todavía sin premio, en orden ascendente a partir de {@code objetivo}, con vuelta al inicio. */
    private static Boleto siguienteVendidoLibre(NavigableMap<Integer, Boleto> vendidos, Set<Integer> tomados, int objetivo) {
        for (Boleto b : vendidos.tailMap(objetivo, false).values()) {
            if (!tomados.contains(b.getNumero())) {
                return b;
            }
        }
        for (Boleto b : vendidos.headMap(objetivo, true).values()) {
            if (!tomados.contains(b.getNumero())) {
                return b;
            }
        }
        // El servicio ya exigió vendidos >= premios, así que siempre queda alguno libre.
        throw new IllegalStateException("No quedan boletos vendidos sin premio para adjudicar.");
    }
}
