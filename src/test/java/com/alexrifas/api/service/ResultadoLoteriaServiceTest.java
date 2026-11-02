package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alexrifas.api.config.LoteriasProperties;
import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.model.TipoLoteria;
import com.alexrifas.api.repository.GanadorRepository;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.repository.ResultadoLoteriaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ResultadoLoteriaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z"); // 07:00 en Colombia
    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);
    private static final LocalDate AYER = HOY.minusDays(1);
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);
    private static final UsuarioAutenticado OTRO_ADMIN = new UsuarioAutenticado(2L, Rol.ADMIN);

    private LoteriaRepository loterias;
    private ResultadoLoteriaRepository resultados;
    private GanadorRepository ganadores;
    private AuditoriaService auditoria;
    private Loteria bogota;

    @BeforeEach
    void setUp() {
        loterias = mock(LoteriaRepository.class);
        resultados = mock(ResultadoLoteriaRepository.class);
        ganadores = mock(GanadorRepository.class);
        auditoria = mock(AuditoriaService.class);
        bogota = new Loteria();
        ReflectionTestUtils.setField(bogota, "id", 5L);
        bogota.setNombre("Lotería de Bogotá");
        bogota.setTipo(TipoLoteria.LOTERIA);
        bogota.setSitioOficialUrl("https://loteriadebogota.com");
        bogota.setActiva(true);
        when(loterias.findById(5L)).thenReturn(Optional.of(bogota));
        when(resultados.save(any(ResultadoLoteria.class))).thenAnswer(inv -> {
            ResultadoLoteria r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", 70L);
            }
            return r;
        });
    }

    private ResultadoLoteriaService servicio(boolean exigirSegundoVerificador) {
        return new ResultadoLoteriaService(loterias, resultados, ganadores, auditoria,
            new LoteriasProperties(exigirSegundoVerificador), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static LoteriaDtos.CargarResultadoRequest carga(LocalDate fecha, String numero) {
        return new LoteriaDtos.CargarResultadoRequest(fecha, numero, "012", 4321, "Sitio oficial",
            " https://loteriadebogota.com/resultado ", "  captura revisada ");
    }

    private ResultadoLoteria resultado(long id, EstadoResultado estado, Long cargadoPor) {
        ResultadoLoteria r = new ResultadoLoteria();
        ReflectionTestUtils.setField(r, "id", id);
        r.setLoteriaId(5L);
        r.setFechaSorteo(AYER);
        r.setNumeroMayor("8954");
        r.setFuente("Sitio oficial");
        r.setEstado(estado);
        r.setCargadoPor(cargadoPor);
        when(resultados.findById(id)).thenReturn(Optional.of(r));
        when(resultados.findByIdParaActualizar(id)).thenReturn(Optional.of(r));
        return r;
    }

    private static int estado(Runnable accion) {
        return assertThrows(ResponseStatusException.class, accion::run).getStatusCode().value();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    // ---- autorización ----

    @Test
    void soloUnAdministradorPuedeCargarVerificarAnularOListar() {
        var anulacion = new LoteriaDtos.AnularResultadoRequest("Número mal copiado");
        for (UsuarioAutenticado actor : new UsuarioAutenticado[] {
                new UsuarioAutenticado(3L, Rol.ORGANIZADOR), new UsuarioAutenticado(4L, Rol.CLIENTE), null}) {
            var s = servicio(false);
            assertEquals(403, estado(() -> s.cargar(5L, carga(AYER, "8954"), actor)));
            assertEquals(403, estado(() -> s.verificar(70L, actor)));
            assertEquals(403, estado(() -> s.anular(70L, anulacion, actor)));
            assertEquals(403, estado(() -> s.listar(5L, null, 0, 20, actor)));
        }
        verifyNoInteractions(resultados, auditoria);
    }

    // ---- cargar ----

    @Test
    void cargarGuardaPendienteDeVerificacionAuditaYMuestraQueBoletoGanaEnCadaTamano() {
        var dto = servicio(false).cargar(5L, carga(AYER, " 8954 "), ADMIN);

        // El ejemplo del negocio: con 8954 salen 54 (100 números), 954 (1000) y 8954 (10 000).
        assertEquals("8954", dto.numeroMayor());
        assertEquals("54", dto.ultimos2());
        assertEquals("954", dto.ultimos3());
        assertEquals("8954", dto.ultimos4());
        assertEquals(EstadoResultado.PENDIENTE_VERIFICACION, dto.estado());
        assertEquals("Lotería de Bogotá", dto.loteria());
        assertEquals("https://loteriadebogota.com/resultado", dto.evidenciaUrl());
        assertEquals("captura revisada", dto.notas());
        assertEquals(1L, dto.cargadoPor());
        assertEquals(70L, dto.id());

        ArgumentCaptor<ResultadoLoteria> guardado = ArgumentCaptor.forClass(ResultadoLoteria.class);
        verify(resultados).save(guardado.capture());
        assertEquals(AHORA, guardado.getValue().getCreadoEn());
        assertEquals(5L, guardado.getValue().getLoteriaId());

        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(ADMIN), eq("resultado_loteria"), eq(70L), eq("RESULTADO_CARGADO"), detalles.capture());
        assertEquals("8954", detalles.getValue().get("numeroMayor"));
        assertEquals("Lotería de Bogotá", detalles.getValue().get("loteria"));
        assertEquals(AYER.toString(), detalles.getValue().get("fechaSorteo"));
    }

    @Test
    void cargarConservaElCeroInicialDe0377() {
        var dto = servicio(false).cargar(5L, carga(AYER, "0377"), ADMIN);

        assertEquals("0377", dto.numeroMayor());
        assertEquals("77", dto.ultimos2());
        assertEquals("377", dto.ultimos3());
    }

    @Test
    void cargarAceptaElSorteoDeHoyPeroNoUnoFuturo() {
        assertEquals(HOY, servicio(false).cargar(5L, carga(HOY, "8954"), ADMIN).fechaSorteo());

        assertEquals(400, estado(() -> servicio(false).cargar(5L, carga(HOY.plusDays(1), "8954"), ADMIN)));
    }

    @Test
    void laFechaDeHoyEsLaDeColombiaNoLaDeUtc() {
        // 03:00 UTC del 3-nov son las 22:00 del 2-nov en Colombia: el 3-nov todavía es futuro.
        var servicioNocturno = new ResultadoLoteriaService(loterias, resultados, ganadores, auditoria, new LoteriasProperties(false),
            Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneOffset.UTC));

        assertEquals(400, estado(() -> servicioNocturno.cargar(5L, carga(LocalDate.of(2026, 11, 3), "8954"), ADMIN)));
        assertEquals(HOY, servicioNocturno.cargar(5L, carga(HOY, "8954"), ADMIN).fechaSorteo());
    }

    @Test
    void cargarRechazaDatosInvalidosSinGuardarNiAuditar() {
        var s = servicio(false);
        assertEquals(400, estado(() -> s.cargar(5L, carga(AYER, "895"), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, carga(AYER, "89541"), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, carga(AYER, "89a4"), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, carga(AYER, null), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, carga(null, "8954"), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, new LoteriaDtos.CargarResultadoRequest(
            AYER, "8954", "1234", null, "Oficial", null, null), ADMIN)));
        assertEquals(400, estado(() -> s.cargar(5L, new LoteriaDtos.CargarResultadoRequest(
            AYER, "8954", null, null, "   ", null, null), ADMIN)));
        verify(resultados, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void cargarUnResultadoVigenteRepetidoDevuelve409YLaConsultaIgnoraLosAnulados() {
        when(resultados.existsByLoteriaIdAndFechaSorteoAndEstadoNot(5L, AYER, EstadoResultado.ANULADO)).thenReturn(true);

        assertEquals(409, estado(() -> servicio(false).cargar(5L, carga(AYER, "8954"), ADMIN)));

        verify(resultados, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void trasAnularUnResultadoSePuedeCargarElCorrecto() {
        // Sin resultado vigente (el anterior quedó ANULADO) la carga procede.
        when(resultados.existsByLoteriaIdAndFechaSorteoAndEstadoNot(5L, AYER, EstadoResultado.ANULADO)).thenReturn(false);

        assertEquals(EstadoResultado.PENDIENTE_VERIFICACION, servicio(false).cargar(5L, carga(AYER, "1234"), ADMIN).estado());
    }

    @Test
    void cargarEnLoteriaInexistenteDevuelve404YEnInactivaDevuelve409() {
        when(loterias.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> servicio(false).cargar(99L, carga(AYER, "8954"), ADMIN)));

        bogota.setActiva(false);
        assertEquals(409, estado(() -> servicio(false).cargar(5L, carga(AYER, "8954"), ADMIN)));
        verify(resultados, never()).save(any());
    }

    // ---- verificar ----

    @Test
    void verificarPasaAVerificadoYAuditaQuienLoHizo() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        var dto = servicio(false).verificar(70L, OTRO_ADMIN);

        assertEquals(EstadoResultado.VERIFICADO, dto.estado());
        assertEquals(2L, r.getVerificadoPor());
        assertEquals(AHORA, r.getVerificadoEn());
        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(OTRO_ADMIN), eq("resultado_loteria"), eq(70L), eq("RESULTADO_VERIFICADO"),
            detalles.capture());
        assertEquals(false, detalles.getValue().get("mismaPersonaCargoYVerifico"));
        assertEquals(1L, detalles.getValue().get("cargadoPor"));
    }

    @Test
    void conSegundoVerificadorExigidoQuienCargoNoPuedeVerificar() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        assertEquals(403, estado(() -> servicio(true).verificar(70L, ADMIN)));

        assertEquals(EstadoResultado.PENDIENTE_VERIFICACION, r.getEstado());
        assertNull(r.getVerificadoPor());
        verify(resultados, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void conSegundoVerificadorExigidoOtroAdministradorSiPuedeVerificar() {
        resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        assertEquals(EstadoResultado.VERIFICADO, servicio(true).verificar(70L, OTRO_ADMIN).estado());
    }

    @Test
    void sinSegundoVerificadorExigidoQuienCargoPuedeVerificarPeroQuedaRegistrado() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        assertEquals(EstadoResultado.VERIFICADO, servicio(false).verificar(70L, ADMIN).estado());

        assertEquals(1L, r.getVerificadoPor());
        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(ADMIN), eq("resultado_loteria"), eq(70L), eq("RESULTADO_VERIFICADO"),
            detalles.capture());
        assertEquals(true, detalles.getValue().get("mismaPersonaCargoYVerifico"));
    }

    @Test
    void soloSeVerificanResultadosPendientes() {
        resultado(70L, EstadoResultado.VERIFICADO, 1L);
        resultado(71L, EstadoResultado.ANULADO, 1L);

        assertEquals(409, estado(() -> servicio(false).verificar(70L, OTRO_ADMIN)));
        assertEquals(409, estado(() -> servicio(false).verificar(71L, OTRO_ADMIN)));
        assertEquals(404, estado(() -> servicio(false).verificar(999L, OTRO_ADMIN)));
        verifyNoInteractions(auditoria);
    }

    // ---- anular ----

    @Test
    void anularRequiereMotivoYDejaTodoRegistradoSinBorrarNada() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.VERIFICADO, 1L);

        var dto = servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("  Número mal copiado  "), OTRO_ADMIN);

        assertEquals(EstadoResultado.ANULADO, dto.estado());
        assertEquals("Número mal copiado", r.getMotivoAnulacion());
        assertEquals(2L, r.getAnuladoPor());
        assertEquals(AHORA, r.getAnuladoEn());
        verify(resultados, never()).delete(any());
        verify(resultados, never()).deleteById(any());
        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(OTRO_ADMIN), eq("resultado_loteria"), eq(70L), eq("RESULTADO_ANULADO"),
            detalles.capture());
        assertEquals("VERIFICADO", detalles.getValue().get("estadoAnterior"));
        assertEquals("Número mal copiado", detalles.getValue().get("motivo"));
    }

    @Test
    void noSePuedeAnularUnResultadoQueYaDecidioUnPremio() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.VERIFICADO, 1L);
        when(ganadores.existsByResultadoLoteriaId(70L)).thenReturn(true);

        assertEquals(409, estado(() ->
            servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("Número mal copiado"), OTRO_ADMIN)));

        assertEquals(EstadoResultado.VERIFICADO, r.getEstado());
        verify(resultados, never()).save(any(ResultadoLoteria.class));
        verifyNoInteractions(auditoria);
    }

    @Test
    void unResultadoSinPremiosSigueSiendoAnulable() {
        resultado(70L, EstadoResultado.VERIFICADO, 1L);
        when(ganadores.existsByResultadoLoteriaId(70L)).thenReturn(false);

        var dto = servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("Número mal copiado"), OTRO_ADMIN);

        assertEquals(EstadoResultado.ANULADO, dto.estado());
    }

    @Test
    void anularUnPendienteTambienEsValido() {
        resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        assertEquals(EstadoResultado.ANULADO,
            servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("Fecha equivocada"), ADMIN).estado());
    }

    @Test
    void anularSinMotivoOUnoMuyCortoDevuelve400() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);

        assertEquals(400, estado(() -> servicio(false).anular(70L, null, ADMIN)));
        assertEquals(400, estado(() -> servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("  "), ADMIN)));
        assertEquals(400, estado(() -> servicio(false).anular(70L, new LoteriaDtos.AnularResultadoRequest("ok"), ADMIN)));

        assertEquals(EstadoResultado.PENDIENTE_VERIFICACION, r.getEstado());
        verifyNoInteractions(auditoria);
    }

    @Test
    void anularUnResultadoYaAnuladoODesconocidoDevuelveError() {
        resultado(71L, EstadoResultado.ANULADO, 1L);

        assertEquals(409, estado(() -> servicio(false).anular(71L, new LoteriaDtos.AnularResultadoRequest("Otra vez"), ADMIN)));
        assertEquals(404, estado(() -> servicio(false).anular(999L, new LoteriaDtos.AnularResultadoRequest("No existe"), ADMIN)));
    }

    // ---- listar ----

    @Test
    void listarOrdenaPorLosMasRecientesAcotaElTamanoYFiltraPorEstado() {
        ResultadoLoteria r = resultado(70L, EstadoResultado.PENDIENTE_VERIFICACION, 1L);
        when(resultados.findByLoteriaIdAndEstado(eq(5L), eq(EstadoResultado.PENDIENTE_VERIFICACION), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 100), 1));
        when(resultados.findByLoteriaId(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var pendientes = servicio(false).listar(5L, EstadoResultado.PENDIENTE_VERIFICACION, -3, 5000, ADMIN);

        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(resultados).findByLoteriaIdAndEstado(eq(5L), eq(EstadoResultado.PENDIENTE_VERIFICACION), captura.capture());
        assertEquals(0, captura.getValue().getPageNumber());
        assertEquals(100, captura.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "fechaSorteo").and(Sort.by(Sort.Direction.DESC, "id")),
            captura.getValue().getSort());
        assertEquals(1, pendientes.contenido().size());
        assertEquals("54", pendientes.contenido().get(0).ultimos2());
        assertEquals("Lotería de Bogotá", pendientes.contenido().get(0).loteria());

        assertTrue(servicio(false).listar(5L, null, 0, 20, ADMIN).contenido().isEmpty());
        verify(resultados).findByLoteriaId(eq(5L), any(Pageable.class));
    }

    @Test
    void listarDeUnaLoteriaInexistenteDevuelve404() {
        when(loterias.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> servicio(false).listar(99L, null, 0, 20, ADMIN)));
    }
}
