package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.repository.UsuarioRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RifaServiceTest {
    @Mock RifaRepository rifas;
    @Mock PremioRepository premios;
    @Mock BoletoRepository boletos;
    @Mock UsuarioRepository usuarios;
    private RifaService service;

    @BeforeEach
    void setUp() {
        service = new RifaService(rifas, premios, boletos, usuarios,
            Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
        Usuario admin = mock(Usuario.class);
        when(admin.isActivo()).thenReturn(true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
    }

    @Test
    void noPermiteCrearRifaConMasDeCincuentaMilBoletos() {
        var request = new RifaDtos.RifaRequest("Rifa de prueba", "Descripción", null,
            new BigDecimal("1000.00"), "COP", 50_001,
            Instant.parse("2026-12-20T18:00:00Z"), Instant.parse("2026-12-19T18:00:00Z"),
            5, com.alexrifas.api.model.MetodoSorteo.ALEATORIO, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crear(request, new UsuarioAutenticado(1L, Rol.ADMIN)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(rifas, never()).save(any());
        verify(boletos, never()).saveAll(any());
    }
}
