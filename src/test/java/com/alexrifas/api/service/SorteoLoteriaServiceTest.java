package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Ganador;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.event.SorteoProcesadoEvent;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.ReglaVacante;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.GanadorRepository;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.ResultadoLoteriaRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class SorteoLoteriaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-12-21T15:00:00Z");
    // 02:00 UTC del 21-dic = 21:00 del 20-dic en Colombia: la cadena de resultados empieza el 20, no el 21.
    private static final Instant FECHA_SORTEO_RIFA = Instant.parse("2026-12-21T02:00:00Z");
    private static final LocalDate DESDE = LocalDate.of(2026, 12, 20);
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);

    private RifaRepository rifas;
    private PremioRepository premios;
    private BoletoRepository boletos;
    private GanadorRepository ganadores;
    private LoteriaRepository loterias;
    private ResultadoLoteriaRepository resultados;
    private AuditoriaService auditoria;
    private ApplicationEventPublisher eventos;
    private SorteoLoteriaService servicio;
    private Rifa rifa;

    @BeforeEach
    void setUp() {
        rifas = mock(RifaRepository.class);
        premios = mock(PremioRepository.class);
        boletos = mock(BoletoRepository.class);
        ganadores = mock(GanadorRepository.class);
        loterias = mock(LoteriaRepository.class);
        resultados = mock(ResultadoLoteriaRepository.class);
        auditoria = mock(AuditoriaService.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new SorteoLoteriaService(rifas, premios, boletos, ganadores, loterias, resultados, auditoria,
            eventos, Clock.fixed(AHORA, ZoneOffset.UTC));

        rifa = new Rifa();
        ReflectionTestUtils.setField(rifa, "id", 3L);
        rifa.setCodigoPublico("ABC123");
        rifa.setTitulo("Rifa de lotería");
        rifa.setOrganizadorId(12L);
        rifa.setCantidadBoletos(100);
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setMetodoSorteo(MetodoSorteo.LOTERIA);
        rifa.setLoteriaId(5L);
        rifa.setDigitos(2);
        rifa.setReglaVacante(ReglaVacante.REPETIR_SIGUIENTE_LOTERIA);
        rifa.setFechaSorteo(FECHA_SORTEO_RIFA);
        when(rifas.findById(3L)).thenReturn(Optional.of(rifa));

        Loteria bogota = new Loteria();
        ReflectionTestUtils.setField(bogota, "id", 5L);
        bogota.setNombre("Lotería de Bogotá");
        when(loterias.findById(5L)).thenReturn(Optional.of(bogota));

        when(boletos.countByRifaIdAndEstado(3L, EstadoBoleto.RESERVADO)).thenReturn(0L);
        when(ganadores.existsByRifaId(3L)).thenReturn(false);
        conPremios(1);
        conVendidos(10, 20, 30);
    }

    private void conPremios(int cantidad) {
        List<Premio> lista = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            Premio p = new Premio();
            ReflectionTestUtils.setField(p, "id", 100L + i);
            p.setPosicion(i);
            p.setNombre("Premio " + i);
            lista.add(p);
        }
        when(premios.findByRifaIdOrderByPosicionAsc(3L)).thenReturn(lista);
    }

    private void conVendidos(int... numeros) {
        List<Boleto> lista = new ArrayList<>();
        for (int n : numeros) {
            Boleto b = new Boleto();
            ReflectionTestUtils.setField(b, "id", 1000L + n);
            b.setNumero(n);
            b.setEstado(EstadoBoleto.VENDIDO);
            lista.add(b);
        }
        when(boletos.findByRifaIdAndEstadoOrderByNumeroAsc(3L, EstadoBoleto.VENDIDO)).thenReturn(lista);
    }

    private ResultadoLoteria resultado(long id, String mayor, EstadoResultado estado) {
        ResultadoLoteria r = new ResultadoLoteria();
        ReflectionTestUtils.setField(r, "id", id);
        r.setLoteriaId(5L);
        r.setFechaSorteo(DESDE.plusDays(id - 1));
        r.setNumeroMayor(mayor);
        r.setEstado(estado);
        return r;
    }

    private void conResultados(ResultadoLoteria... lista) {
        when(resultados.findVigentesDesde(eq(5L), any())).thenReturn(List.of(lista));
        when(resultados.findVigentesDesdeParaActualizar(eq(5L), any())).thenReturn(List.of(lista));
    }

    private static int estado(Runnable accion) {
        return assertThrows(ResponseStatusException.class, accion::run).getStatusCode().value();
    }

    // ---- ejecutar ----

    @Test
    void ejecutarDecideElPremioGuardaElResultadoQueLoDecidioYDejaLaRifaSorteada() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.ejecutar(3L, ADMIN);

        assertTrue(dto.ejecutado());
        assertTrue(dto.completo());
        assertEquals("Lotería de Bogotá", dto.loteria());
        assertEquals(1, dto.ganadores().size());
        assertEquals(20, dto.ganadores().get(0).numeroBoleto());
        assertEquals("20", dto.ganadores().get(0).numeroBoletoTexto());
        assertEquals(EstadoRifa.SORTEADA, rifa.getEstado());

        ArgumentCaptor<List<Ganador>> guardados = listaCaptor();
        verify(ganadores).saveAll(guardados.capture());
        Ganador g = guardados.getValue().get(0);
        assertEquals(1020L, g.getBoletoId());
        assertEquals(101L, g.getPremioId());
        assertEquals(1L, g.getResultadoLoteriaId());
        assertEquals(20, g.getNumeroResultado());
        assertFalse(g.isAdjudicadoPorVacante());
        assertTrue(g.isPublicado());
        verify(rifas).save(rifa);
        verify(eventos).publishEvent(any(SorteoProcesadoEvent.class));
    }

    @Test
    void ejecutarLeeLosResultadosConBloqueoYDesdeLaFechaDeColombia() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));

        servicio.ejecutar(3L, ADMIN);

        verify(resultados).findVigentesDesdeParaActualizar(5L, DESDE);
        verify(resultados, never()).findVigentesDesde(any(), any());
    }

    @Test
    void ejecutarAuditaConElDetalleDeCadaPaso() {
        conResultados(resultado(1, "8915", EstadoResultado.VERIFICADO), resultado(2, "0020", EstadoResultado.VERIFICADO));

        servicio.ejecutar(3L, ADMIN);

        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(ADMIN), eq("rifa"), eq(3L), eq("SORTEO_LOTERIA_EJECUTADO"), detalles.capture());
        assertEquals("Lotería de Bogotá", detalles.getValue().get("loteria"));
        assertEquals(2, detalles.getValue().get("digitos"));
        List<?> pasos = (List<?>) detalles.getValue().get("pasos");
        assertEquals(2, pasos.size());
        assertEquals("REPETIDO", ((Map<?, ?>) pasos.get(0)).get("resolucion"));
        assertEquals("NO_VENDIDO", ((Map<?, ?>) pasos.get(0)).get("causa"));
        assertEquals("DIRECTO", ((Map<?, ?>) pasos.get(1)).get("resolucion"));
    }

    @Test
    void conReglaAdjudicarElCupoVacioPasaAlSiguienteVendidoYQuedaMarcado() {
        rifa.setReglaVacante(ReglaVacante.ADJUDICAR_SIGUIENTE_VENDIDO);
        conResultados(resultado(1, "8925", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.ejecutar(3L, ADMIN);

        assertEquals(30, dto.ganadores().get(0).numeroBoleto());
        assertEquals("25", dto.ganadores().get(0).numeroObjetivo());
        assertTrue(dto.ganadores().get(0).adjudicadoPorVacante());
        ArgumentCaptor<List<Ganador>> guardados = listaCaptor();
        verify(ganadores).saveAll(guardados.capture());
        assertTrue(guardados.getValue().get(0).isAdjudicadoPorVacante());
        assertEquals(25, guardados.getValue().get(0).getNumeroResultado());
    }

    @Test
    void ejecutarEsAtomico_siFaltaUnResultadoNoGuardaNada() {
        conPremios(2);
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO)); // alcanza para 1 de 2 premios

        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));

        verify(ganadores, never()).saveAll(anyList());
        verify(rifas, never()).save(any());
        verifyNoInteractions(auditoria, eventos);
        assertEquals(EstadoRifa.CERRADA, rifa.getEstado());
    }

    @Test
    void ejecutarConUnResultadoPendienteDevuelve409ConMensajeClaro() {
        conResultados(resultado(1, "8920", EstadoResultado.PENDIENTE_VERIFICACION));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> servicio.ejecutar(3L, ADMIN));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("pendiente de verificación"));
        verify(ganadores, never()).saveAll(anyList());
    }

    // ---- previsualizar ----

    @Test
    void previsualizarMuestraElGanadorSinGuardarNiCambiarLaRifa() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.previsualizar(3L, ADMIN);

        assertTrue(dto.completo());
        assertFalse(dto.ejecutado());
        assertEquals(20, dto.ganadores().get(0).numeroBoleto());
        assertEquals("DIRECTO", dto.pasos().get(0).resolucion());
        assertNull(dto.pasos().get(0).causa());
        assertEquals(EstadoRifa.CERRADA, rifa.getEstado());
        verify(ganadores, never()).saveAll(anyList());
        verify(resultados, never()).findVigentesDesdeParaActualizar(any(), any());
        verifyNoInteractions(auditoria, eventos);
    }

    @Test
    void previsualizarIncompletoNoEsUnErrorYDiceQueHacer() {
        conPremios(2);
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.previsualizar(3L, ADMIN);

        assertFalse(dto.completo());
        assertEquals(1, dto.ganadores().size());
        assertTrue(dto.mensaje().contains("Falta un resultado verificado"));
    }

    @Test
    void losNumerosVanConCerosALaIzquierda() {
        rifa.setCantidadBoletos(1000);
        rifa.setDigitos(3);
        conVendidos(7, 377);
        conResultados(resultado(1, "0377", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.previsualizar(3L, ADMIN);

        assertEquals("377", dto.ganadores().get(0).numeroBoletoTexto());
        assertEquals("377", dto.pasos().get(0).numeroObjetivo());
        assertEquals("0377", dto.pasos().get(0).numeroMayor());
    }

    @Test
    void losBoletosPequenosSeMuestranConCeros() {
        conVendidos(7, 20, 30);
        conResultados(resultado(1, "1207", EstadoResultado.VERIFICADO));

        SorteoDtos.SorteoLoteriaDto dto = servicio.previsualizar(3L, ADMIN);

        assertEquals("07", dto.ganadores().get(0).numeroBoletoTexto());
        assertEquals("07", dto.pasos().get(0).numeroObjetivo());
    }

    // ---- precondiciones (valen para previsualizar y ejecutar) ----

    @Test
    void soloUnAdministradorPuedeSortearOPrevisualizar() {
        var organizador = new UsuarioAutenticado(12L, Rol.ORGANIZADOR);
        assertEquals(403, estado(() -> servicio.ejecutar(3L, organizador)));
        assertEquals(403, estado(() -> servicio.previsualizar(3L, organizador)));
        assertEquals(403, estado(() -> servicio.ejecutar(3L, null)));
        verifyNoInteractions(rifas);
    }

    @Test
    void rifaInexistenteDevuelve404() {
        assertEquals(404, estado(() -> servicio.ejecutar(99L, ADMIN)));
    }

    @Test
    void unaRifaNoConfiguradaPorLoteriaDevuelve409() {
        rifa.setDigitos(null);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));
        rifa.setDigitos(2);
        rifa.setMetodoSorteo(MetodoSorteo.ALEATORIO);
        assertEquals(409, estado(() -> servicio.previsualizar(3L, ADMIN)));
    }

    @Test
    void laRifaDebeEstarCerradaYSinSorteoPrevio() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));
        rifa.setEstado(EstadoRifa.PUBLICADA);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));

        rifa.setEstado(EstadoRifa.CERRADA);
        when(ganadores.existsByRifaId(3L)).thenReturn(true);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));

        rifa.setEstado(EstadoRifa.SORTEADA);
        when(ganadores.existsByRifaId(3L)).thenReturn(false);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));
        verify(ganadores, never()).saveAll(anyList());
    }

    @Test
    void conBoletosReservadosNoSeSortea() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));
        when(boletos.countByRifaIdAndEstado(3L, EstadoBoleto.RESERVADO)).thenReturn(2L);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));
    }

    @Test
    void sinPremiosOSinSuficientesVendidosNoSeSortea() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));
        conPremios(4);
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN))); // 3 vendidos < 4 premios

        when(premios.findByRifaIdOrderByPosicionAsc(3L)).thenReturn(List.of());
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));
    }

    @Test
    void unaConfiguracionInconsistenteDeDigitosYCantidadSeRechaza() {
        conResultados(resultado(1, "8920", EstadoResultado.VERIFICADO));
        rifa.setDigitos(3); // 100 boletos con 3 dígitos
        assertEquals(409, estado(() -> servicio.ejecutar(3L, ADMIN)));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Ganador>> listaCaptor() {
        return ArgumentCaptor.forClass((Class<List<Ganador>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
