package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

class GrillaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-22T12:00:00Z");
    private RifaRepository rifas;
    private GrillaService servicio;
    private Rifa rifa;

    @BeforeEach
    void setUp() {
        rifas = mock(RifaRepository.class);
        servicio = new GrillaService(rifas, mock(BoletoRepository.class), mock(StringRedisTemplate.class),
            new ObjectMapper(), Clock.fixed(AHORA, ZoneOffset.UTC));
        rifa = new Rifa();
        rifa.setCodigoPublico("ABC123");
        rifa.setCantidadBoletos(10);
        rifa.setEstado(EstadoRifa.PUBLICADA);
    }

    private BoletoRepository.OcupadoView ocupado(int numero, EstadoBoleto estado, Instant hasta) {
        var v = mock(BoletoRepository.OcupadoView.class);
        when(v.getNumero()).thenReturn(numero);
        when(v.getEstado()).thenReturn(estado);
        when(v.getReservadoHasta()).thenReturn(hasta);
        return v;
    }

    @Test
    void marcaVendidosReservadosYTrataLaReservaVencidaComoLibre() {
        RifaDtos.GrillaBoletos g = servicio.construir(rifa, List.of(
            ocupado(1, EstadoBoleto.VENDIDO, null),
            ocupado(3, EstadoBoleto.RESERVADO, AHORA.plusSeconds(60)),
            ocupado(5, EstadoBoleto.RESERVADO, AHORA.minusSeconds(1))), AHORA);
        assertEquals("LVLRLLLLLL", g.estados());
        assertEquals(10, g.cantidad());
        assertEquals(1, g.vendidos());
        assertEquals(1, g.reservados());
        assertEquals(8, g.libres());
    }

    @Test
    void rifaEnBorradorNoEsVisible() {
        rifa.setEstado(EstadoRifa.BORRADOR);
        when(rifas.findByCodigoPublico("ABC123")).thenReturn(Optional.of(rifa));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> servicio.grilla("abc123"))
            .getStatusCode().value());
    }
}
