package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.CausaVacante;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.model.ReglaVacante;
import com.alexrifas.api.model.ResolucionPaso;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Reglas puras del sorteo por lotería: sin mocks ni base de datos. */
class SorteoLoteriaCalculadorTest {
    private static final ReglaVacante REPETIR = ReglaVacante.REPETIR_SIGUIENTE_LOTERIA;
    private static final ReglaVacante ADJUDICAR = ReglaVacante.ADJUDICAR_SIGUIENTE_VENDIDO;

    private static Premio premio(long id, int posicion) {
        Premio p = new Premio();
        ReflectionTestUtils.setField(p, "id", id);
        p.setPosicion(posicion);
        p.setNombre("Premio " + posicion);
        return p;
    }

    private static List<Boleto> vendidos(int... numeros) {
        List<Boleto> lista = new ArrayList<>();
        for (int n : numeros) {
            Boleto b = new Boleto();
            ReflectionTestUtils.setField(b, "id", 1000L + n);
            b.setNumero(n);
            b.setEstado(EstadoBoleto.VENDIDO);
            lista.add(b);
        }
        return lista;
    }

    private static ResultadoLoteria resultado(long id, String mayor, EstadoResultado estado) {
        ResultadoLoteria r = new ResultadoLoteria();
        ReflectionTestUtils.setField(r, "id", id);
        r.setLoteriaId(5L);
        r.setFechaSorteo(LocalDate.of(2026, 12, 20).plusDays(id));
        r.setNumeroMayor(mayor);
        r.setEstado(estado);
        return r;
    }

    private static ResultadoLoteria verificado(long id, String mayor) {
        return resultado(id, mayor, EstadoResultado.VERIFICADO);
    }

    @Test
    void gana_el_boleto_de_los_ultimos_digitos_con_ceros_a_la_izquierda() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 3, REPETIR,
            vendidos(5, 77, 377), List.of(verificado(1, "0377")));

        assertTrue(plan.completo());
        assertEquals(377, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(ResolucionPaso.DIRECTO, plan.pasos().get(0).resolucion());
        assertNull(plan.pasos().get(0).causa());
        assertEquals(false, plan.asignaciones().get(0).porVacante());
    }

    @Test
    void con_dos_digitos_usa_las_dos_ultimas_cifras() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(54, 89), List.of(verificado(1, "8954")));

        assertEquals(54, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(54, plan.asignaciones().get(0).numeroObjetivo());
    }

    @Test
    void cada_premio_consume_un_resultado_distinto_en_orden() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1), premio(2, 2)), 2, REPETIR,
            vendidos(11, 22, 33), List.of(verificado(1, "0022"), verificado(2, "0011"), verificado(3, "0033")));

        assertTrue(plan.completo());
        assertEquals(22, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(1L, plan.asignaciones().get(0).resultado().getId());
        assertEquals(11, plan.asignaciones().get(1).boleto().getNumero());
        assertEquals(2L, plan.asignaciones().get(1).resultado().getId());
    }

    // ---- cupo vacío: REPETIR_SIGUIENTE_LOTERIA ----

    @Test
    void repetir_descarta_el_resultado_del_cupo_vacio_y_usa_el_siguiente() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(10, 20), List.of(verificado(1, "0015"), verificado(2, "0020")));

        assertTrue(plan.completo());
        assertEquals(2, plan.pasos().size());
        assertEquals(ResolucionPaso.REPETIDO, plan.pasos().get(0).resolucion());
        assertEquals(CausaVacante.NO_VENDIDO, plan.pasos().get(0).causa());
        assertNull(plan.pasos().get(0).boleto());
        assertEquals(ResolucionPaso.DIRECTO, plan.pasos().get(1).resolucion());
        assertEquals(20, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(2L, plan.asignaciones().get(0).resultado().getId());
    }

    @Test
    void repetir_sin_mas_resultados_deja_el_sorteo_incompleto() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(10, 20), List.of(verificado(1, "0015")));

        assertEquals(SorteoLoteriaCalculador.Bloqueo.FALTA_RESULTADO, plan.bloqueo());
        assertEquals(1, plan.premioBloqueado());
        assertTrue(plan.asignaciones().isEmpty());
    }

    // ---- cupo vacío: ADJUDICAR_SIGUIENTE_VENDIDO ----

    @Test
    void adjudicar_da_el_premio_al_siguiente_numero_vendido() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, ADJUDICAR,
            vendidos(10, 20, 40), List.of(verificado(1, "0025")));

        assertTrue(plan.completo());
        var paso = plan.pasos().get(0);
        assertEquals(ResolucionPaso.ADJUDICADO_SIGUIENTE_VENDIDO, paso.resolucion());
        assertEquals(CausaVacante.NO_VENDIDO, paso.causa());
        assertEquals(25, paso.numeroObjetivo());
        assertEquals(40, plan.asignaciones().get(0).boleto().getNumero());
        assertTrue(plan.asignaciones().get(0).porVacante());
        assertEquals(25, plan.asignaciones().get(0).numeroObjetivo());
    }

    @Test
    void adjudicar_da_la_vuelta_al_inicio_si_no_hay_vendidos_mayores() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, ADJUDICAR,
            vendidos(10, 20), List.of(verificado(1, "0095")));

        assertEquals(10, plan.asignaciones().get(0).boleto().getNumero());
    }

    @Test
    void adjudicar_no_repite_un_boleto_que_ya_gano_otro_premio() {
        // Premio 1 -> 20 directo. Premio 2 -> sale 20 otra vez (ya ganó): pasa al siguiente vendido libre, el 30.
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1), premio(2, 2)), 2, ADJUDICAR,
            vendidos(10, 20, 30), List.of(verificado(1, "0020"), verificado(2, "0020")));

        assertEquals(20, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(30, plan.asignaciones().get(1).boleto().getNumero());
        assertEquals(CausaVacante.YA_GANO_OTRO_PREMIO, plan.pasos().get(1).causa());
        assertTrue(plan.asignaciones().get(1).porVacante());
    }

    @Test
    void adjudicar_salta_los_boletos_que_ya_ganaron_al_buscar_el_siguiente() {
        // Premio 1 -> 30 directo. Premio 2 -> cupo vacío en 25: el siguiente vendido es 30 (tomado), sigue el 40.
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1), premio(2, 2)), 2, ADJUDICAR,
            vendidos(10, 30, 40), List.of(verificado(1, "0030"), verificado(2, "0025")));

        assertEquals(40, plan.asignaciones().get(1).boleto().getNumero());
    }

    // ---- un boleto no gana dos premios ----

    @Test
    void repetir_trata_un_boleto_que_ya_gano_como_cupo_vacio() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1), premio(2, 2)), 2, REPETIR,
            vendidos(10, 20), List.of(verificado(1, "0020"), verificado(2, "0020"), verificado(3, "0010")));

        assertTrue(plan.completo());
        assertEquals(20, plan.asignaciones().get(0).boleto().getNumero());
        assertEquals(10, plan.asignaciones().get(1).boleto().getNumero());
        assertEquals(3L, plan.asignaciones().get(1).resultado().getId());
        assertEquals(ResolucionPaso.REPETIDO, plan.pasos().get(1).resolucion());
        assertEquals(CausaVacante.YA_GANO_OTRO_PREMIO, plan.pasos().get(1).causa());
    }

    // ---- resultados pendientes, anulados y faltantes ----

    @Test
    void se_detiene_ante_un_resultado_pendiente_sin_saltarselo() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(10, 20),
            List.of(resultado(1, "0015", EstadoResultado.VERIFICADO),
                resultado(2, "0020", EstadoResultado.PENDIENTE_VERIFICACION)));

        // El 15 es cupo vacío; el siguiente (20) está pendiente: no se puede saltar ni usar.
        assertEquals(SorteoLoteriaCalculador.Bloqueo.RESULTADO_PENDIENTE, plan.bloqueo());
        assertEquals(2L, plan.pendiente().getId());
        assertTrue(plan.asignaciones().isEmpty());
    }

    @Test
    void un_primer_resultado_pendiente_bloquea_aunque_haya_otros_verificados_despues() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(10, 20),
            List.of(resultado(1, "0010", EstadoResultado.PENDIENTE_VERIFICACION), verificado(2, "0020")));

        assertEquals(SorteoLoteriaCalculador.Bloqueo.RESULTADO_PENDIENTE, plan.bloqueo());
        assertEquals(1L, plan.pendiente().getId());
    }

    @Test
    void los_resultados_anulados_se_ignoran() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR,
            vendidos(10, 20),
            List.of(resultado(1, "0010", EstadoResultado.ANULADO), verificado(2, "0020")));

        assertTrue(plan.completo());
        assertEquals(20, plan.asignaciones().get(0).boleto().getNumero());
    }

    @Test
    void sin_resultados_no_hay_ganadores_y_se_pide_el_resultado() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 2, REPETIR, vendidos(10), List.of());

        assertEquals(SorteoLoteriaCalculador.Bloqueo.FALTA_RESULTADO, plan.bloqueo());
    }

    @Test
    void con_varios_premios_un_plan_parcial_conserva_lo_ya_decidido() {
        var plan = SorteoLoteriaCalculador.calcular(List.of(premio(1, 1), premio(2, 2)), 2, REPETIR,
            vendidos(10, 20), List.of(verificado(1, "0010")));

        assertEquals(SorteoLoteriaCalculador.Bloqueo.FALTA_RESULTADO, plan.bloqueo());
        assertEquals(2, plan.premioBloqueado());
        assertEquals(1, plan.asignaciones().size());
        assertEquals(10, plan.asignaciones().get(0).boleto().getNumero());
    }

    // ---- determinismo y validación ----

    @Test
    void es_determinista_y_cargar_un_resultado_posterior_no_cambia_lo_ya_decidido() {
        var premios = List.of(premio(1, 1), premio(2, 2));
        var corto = SorteoLoteriaCalculador.calcular(premios, 2, REPETIR, vendidos(10, 20),
            List.of(verificado(1, "0010")));
        var largo = SorteoLoteriaCalculador.calcular(premios, 2, REPETIR, vendidos(10, 20),
            List.of(verificado(1, "0010"), verificado(2, "0020")));

        assertEquals(corto.asignaciones().get(0).boleto().getNumero(), largo.asignaciones().get(0).boleto().getNumero());
        assertEquals(corto.asignaciones().get(0).resultado().getId(), largo.asignaciones().get(0).resultado().getId());
        assertTrue(largo.completo());
    }

    @Test
    void rechaza_digitos_distintos_de_2_3_o_4() {
        assertThrows(IllegalArgumentException.class, () ->
            SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 1, REPETIR, vendidos(1), List.of(verificado(1, "0001"))));
        assertThrows(IllegalArgumentException.class, () ->
            SorteoLoteriaCalculador.calcular(List.of(premio(1, 1)), 5, REPETIR, vendidos(1), List.of(verificado(1, "0001"))));
    }
}
