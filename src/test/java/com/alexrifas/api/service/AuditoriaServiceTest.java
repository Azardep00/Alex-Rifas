package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.alexrifas.api.entity.AuditoriaEvento;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.AuditoriaEventoRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class AuditoriaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z");

    private AuditoriaEventoRepository eventos;
    private AuditoriaService servicio;

    @BeforeEach
    void setUp() {
        eventos = mock(AuditoriaEventoRepository.class);
        servicio = new AuditoriaService(eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    void guardaQuienQueCuandoYConQueDetalles() {
        MDC.put("requestId", "req-123");

        servicio.registrar(new UsuarioAutenticado(9L, Rol.ADMIN), "compra", "ABC123", "PAGO_CONFIRMADO_MANUAL",
            Map.of("monto", 20000));

        ArgumentCaptor<AuditoriaEvento> captura = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(eventos).save(captura.capture());
        AuditoriaEvento evento = captura.getValue();
        assertEquals(9L, evento.getUsuarioId());
        assertEquals("compra", evento.getEntidad());
        assertEquals("ABC123", evento.getEntidadId());
        assertEquals("PAGO_CONFIRMADO_MANUAL", evento.getAccion());
        assertEquals("req-123", evento.getRequestId());
        assertEquals(Map.of("monto", 20000), evento.getDetalles());
        assertEquals(AHORA, evento.getCreadoEn());
    }

    @Test
    void elIdNumericoSeGuardaComoTextoYSinActorNiRequestIdSeToleraNulo() {
        servicio.registrar(null, "loteria", 5L, "LOTERIA_CREADA", Map.of());

        ArgumentCaptor<AuditoriaEvento> captura = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(eventos).save(captura.capture());
        assertEquals("5", captura.getValue().getEntidadId());
        assertNull(captura.getValue().getUsuarioId());
        assertNull(captura.getValue().getRequestId());
    }
}
