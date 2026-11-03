package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** CP-17: el organizador puede leer las loterías activas (solo lectura); un cliente no. */
class LoteriaServiceGestorTest {
    private LoteriaRepository loterias;
    private LoteriaService servicio;

    @BeforeEach
    void setUp() {
        loterias = mock(LoteriaRepository.class);
        servicio = new LoteriaService(loterias, mock(AuditoriaService.class),
            Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void organizadorYAdminVenSoloActivas() {
        when(loterias.findByActivaOrderByNombreAsc(true)).thenReturn(List.<Loteria>of());
        assertEquals(0, servicio.listarActivasParaGestor(new UsuarioAutenticado(2L, Rol.ORGANIZADOR)).size());
        assertEquals(0, servicio.listarActivasParaGestor(new UsuarioAutenticado(1L, Rol.ADMIN)).size());
        verify(loterias, org.mockito.Mockito.times(2)).findByActivaOrderByNombreAsc(true);
    }

    @Test
    void clienteYAnonimoRecibenProhibido() {
        var cliente = new UsuarioAutenticado(3L, Rol.CLIENTE);
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> servicio.listarActivasParaGestor(cliente)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> servicio.listarActivasParaGestor(null)).getStatusCode().value());
    }
}
