package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.model.TipoLoteria;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class LoteriaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);

    private LoteriaRepository loterias;
    private AuditoriaService auditoria;
    private LoteriaService servicio;

    @BeforeEach
    void setUp() {
        loterias = mock(LoteriaRepository.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new LoteriaService(loterias, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(loterias.save(any(Loteria.class))).thenAnswer(inv -> {
            Loteria l = inv.getArgument(0);
            if (l.getId() == null) {
                ReflectionTestUtils.setField(l, "id", 7L);
            }
            return l;
        });
    }

    private static LoteriaDtos.LoteriaRequest peticion(String nombre, TipoLoteria tipo, List<Integer> dias,
                                                       Boolean activa) {
        return new LoteriaDtos.LoteriaRequest(nombre, tipo, dias, LocalTime.of(22, 0), " https://ejemplo.co ", activa);
    }

    private static Loteria existente(long id, String nombre) {
        Loteria l = new Loteria();
        ReflectionTestUtils.setField(l, "id", id);
        l.setNombre(nombre);
        l.setTipo(TipoLoteria.LOTERIA);
        l.setDiasSemana("4");
        l.setActiva(true);
        return l;
    }

    private static int estado(Runnable accion) {
        return assertThrows(ResponseStatusException.class, accion::run).getStatusCode().value();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    @Test
    void soloUnAdministradorPuedeListarCrearOEditar() {
        for (UsuarioAutenticado actor : new UsuarioAutenticado[] {
                new UsuarioAutenticado(2L, Rol.ORGANIZADOR), new UsuarioAutenticado(3L, Rol.CLIENTE), null}) {
            assertEquals(403, estado(() -> servicio.listar(null, actor)));
            assertEquals(403, estado(() -> servicio.crear(peticion("Bogotá", TipoLoteria.LOTERIA, null, null), actor)));
            assertEquals(403, estado(() -> servicio.actualizar(1L, peticion("Bogotá", TipoLoteria.LOTERIA, null, null), actor)));
        }
        verifyNoInteractions(loterias, auditoria);
    }

    @Test
    void crearNormalizaElNombreOrdenaLosDiasYAudita() {
        var dto = servicio.crear(peticion("  Lotería   de  Bogotá ", TipoLoteria.LOTERIA, List.of(4, 1, 4), null), ADMIN);

        assertEquals("Lotería de Bogotá", dto.nombre());
        assertEquals(List.of(1, 4), dto.diasSemana());
        assertEquals("https://ejemplo.co", dto.sitioOficialUrl());
        assertTrue(dto.activa()); // sin indicar, nace activa
        assertEquals(7L, dto.id());
        verify(loterias).existsByNombreIgnoreCase("Lotería de Bogotá");

        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(ADMIN), eq("loteria"), eq(7L), eq("LOTERIA_CREADA"), detalles.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> despues = (Map<String, Object>) detalles.getValue().get("despues");
        assertEquals("Lotería de Bogotá", despues.get("nombre"));
        assertEquals("1,4", despues.get("diasSemana"));
    }

    @Test
    void crearUnChanceDeUnaFranjaDiariaSinDiasQuedaSinDefinir() {
        var dto = servicio.crear(peticion("Dorado Noche", TipoLoteria.CHANCE, List.of(), true), ADMIN);

        assertEquals(TipoLoteria.CHANCE, dto.tipo());
        assertEquals(List.of(), dto.diasSemana());
    }

    @Test
    void crearConNombreRepetidoSinImportarMayusculasDevuelve409() {
        when(loterias.existsByNombreIgnoreCase("Dorado Noche")).thenReturn(true);

        assertEquals(409, estado(() -> servicio.crear(peticion("Dorado Noche", TipoLoteria.CHANCE, null, null), ADMIN)));

        verify(loterias, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void crearConNombreEnBlancoODiaInvalidoDevuelve400() {
        assertEquals(400, estado(() -> servicio.crear(peticion("   ", TipoLoteria.LOTERIA, null, null), ADMIN)));
        assertEquals(400, estado(() -> servicio.crear(peticion("Bogotá", TipoLoteria.LOTERIA, List.of(8), null), ADMIN)));
        assertEquals(400, estado(() -> servicio.crear(peticion("Bogotá", TipoLoteria.LOTERIA, List.of(0), null), ADMIN)));
        verify(loterias, never()).save(any());
    }

    @Test
    void actualizarRegistraElAntesYElDespuesYConservaActivaSiNoSeEnvia() {
        Loteria l = existente(3L, "Cundinamarca");
        l.setActiva(false);
        when(loterias.findById(3L)).thenReturn(Optional.of(l));

        var dto = servicio.actualizar(3L, peticion("Cundinamarca", TipoLoteria.LOTERIA, List.of(1), null), ADMIN);

        assertFalse(dto.activa());
        assertEquals(List.of(1), dto.diasSemana());
        assertEquals(AHORA, l.getActualizadaEn());
        ArgumentCaptor<Map<String, Object>> detalles = mapCaptor();
        verify(auditoria).registrar(eq(ADMIN), eq("loteria"), eq(3L), eq("LOTERIA_ACTUALIZADA"), detalles.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> antes = (Map<String, Object>) detalles.getValue().get("antes");
        @SuppressWarnings("unchecked")
        Map<String, Object> despues = (Map<String, Object>) detalles.getValue().get("despues");
        assertEquals("4", antes.get("diasSemana"));
        assertEquals("1", despues.get("diasSemana"));
    }

    @Test
    void actualizarPuedeDesactivarYReactivar() {
        Loteria l = existente(3L, "Cundinamarca");
        when(loterias.findById(3L)).thenReturn(Optional.of(l));

        assertFalse(servicio.actualizar(3L, peticion("Cundinamarca", TipoLoteria.LOTERIA, null, false), ADMIN).activa());
        assertTrue(servicio.actualizar(3L, peticion("Cundinamarca", TipoLoteria.LOTERIA, null, true), ADMIN).activa());
    }

    @Test
    void actualizarInexistenteDevuelve404YNombreDeOtraLoteriaDevuelve409() {
        when(loterias.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> servicio.actualizar(99L, peticion("X", TipoLoteria.LOTERIA, null, null), ADMIN)));

        when(loterias.findById(3L)).thenReturn(Optional.of(existente(3L, "Cundinamarca")));
        when(loterias.existsByNombreIgnoreCaseAndIdNot("Bogotá", 3L)).thenReturn(true);
        assertEquals(409, estado(() -> servicio.actualizar(3L, peticion("Bogotá", TipoLoteria.LOTERIA, null, null), ADMIN)));
        verify(loterias, never()).save(any());
    }

    @Test
    void listarFiltraPorActivaYSinFiltroDevuelveTodas() {
        when(loterias.findAllByOrderByNombreAsc()).thenReturn(List.of(existente(1L, "A"), existente(2L, "B")));
        when(loterias.findByActivaOrderByNombreAsc(true)).thenReturn(List.of(existente(1L, "A")));

        assertEquals(2, servicio.listar(null, ADMIN).size());
        assertEquals(1, servicio.listar(true, ADMIN).size());
        assertNull(servicio.listar(true, ADMIN).get(0).horaSorteo());
    }
}
