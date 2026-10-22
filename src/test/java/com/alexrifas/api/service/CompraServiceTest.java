package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.PagoWompiRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CompraServiceTest {
    private CompraRepository compras;
    private BoletoRepository boletos;
    private RifaRepository rifas;
    private PagoWompiRepository pagos;
    private CompraService servicio;

    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        boletos = mock(BoletoRepository.class);
        rifas = mock(RifaRepository.class);
        pagos = mock(PagoWompiRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        servicio = new CompraService(compras, boletos, rifas, pagos, clock);
    }

    @Test
    void rechazaNumerosRepetidosAntesDeConsultarLaBaseDeDatos() {
        var request = new CompraDtos.ReservaRequest("ABC123", List.of(4, 4));
        var actor = new UsuarioAutenticado(7L, Rol.CLIENTE);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservar(request, actor));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(compras, boletos, rifas, pagos);
    }

    @Test
    void soloLosClientesPuedenReservar() {
        var request = new CompraDtos.ReservaRequest("ABC123", List.of(4));
        var actor = new UsuarioAutenticado(7L, Rol.ORGANIZADOR);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservar(request, actor));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(compras, boletos, rifas, pagos);
    }
}
