package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.event.SorteoProcesadoEvent;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.GanadorRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

class SorteoServiceTest {
    private RifaRepository rifas;
    private PremioRepository premios;
    private BoletoRepository boletos;
    private GanadorRepository ganadores;
    private ApplicationEventPublisher eventos;
    private SorteoService servicio;

    @BeforeEach
    void setUp() {
        rifas = mock(RifaRepository.class);
        premios = mock(PremioRepository.class);
        boletos = mock(BoletoRepository.class);
        ganadores = mock(GanadorRepository.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new SorteoService(rifas, premios, boletos, ganadores, eventos,
            Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sorteoAleatorioRegistraGanadorYCambiaEstado() {
        Rifa rifa = new Rifa();
        rifa.setCodigoPublico("ABC123");
        rifa.setOrganizadorId(12L);
        rifa.setTitulo("Rifa de prueba");
        rifa.setCantidadBoletos(10);
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setMetodoSorteo(MetodoSorteo.ALEATORIO);
        when(rifas.findById(3L)).thenReturn(Optional.of(rifa));
        when(ganadores.existsByRifaId(3L)).thenReturn(false);
        when(boletos.countByRifaIdAndEstado(3L, EstadoBoleto.RESERVADO)).thenReturn(0L);
        Premio premio = new Premio();
        premio.setPosicion(1);
        premio.setNombre("Bono");
        when(premios.findByRifaIdOrderByPosicionAsc(3L)).thenReturn(List.of(premio));
        Boleto boleto = new Boleto();
        boleto.setNumero(4);
        boleto.setEstado(EstadoBoleto.VENDIDO);
        when(boletos.findByRifaIdAndEstadoOrderByNumeroAsc(3L, EstadoBoleto.VENDIDO)).thenReturn(List.of(boleto));

        List<SorteoDtos.GanadorDto> resultado = servicio.sortear(3L, null,
            new UsuarioAutenticado(12L, Rol.ORGANIZADOR));

        assertEquals(1, resultado.size());
        assertEquals("Bono", resultado.get(0).premio());
        assertEquals(EstadoRifa.SORTEADA, rifa.getEstado());
        verify(ganadores).saveAll(any());
        verify(eventos).publishEvent(any(SorteoProcesadoEvent.class));
    }

    @Test
    void elSorteoManualEstaDesactivadoParaUnaRifaConfiguradaPorLoteria() {
        Rifa rifa = new Rifa();
        rifa.setOrganizadorId(12L);
        rifa.setCantidadBoletos(100);
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setMetodoSorteo(MetodoSorteo.LOTERIA);
        rifa.setLoteriaId(5L);
        rifa.setDigitos(2);
        when(rifas.findById(3L)).thenReturn(Optional.of(rifa));

        // Ni el organizador dueño ni un administrador pueden escribir el número a mano.
        for (var actor : List.of(new UsuarioAutenticado(12L, Rol.ORGANIZADOR), new UsuarioAutenticado(1L, Rol.ADMIN))) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.sortear(3L, new SorteoDtos.SorteoRequest(List.of(7)), actor));
            assertEquals(409, ex.getStatusCode().value());
            // Debe fallar por la regla de lotería y no por otra causa (premios, vendidos...).
            org.junit.jupiter.api.Assertions.assertTrue(ex.getReason().contains("resultado de la lotería"));
        }
        assertEquals(EstadoRifa.CERRADA, rifa.getEstado());
        verify(ganadores, never()).saveAll(anyList());
        verifyNoInteractions(eventos);
    }

    @Test
    void unaRifaLoteriaAntiguaSinDigitosSigueConElSorteoManual() {
        Rifa rifa = new Rifa();
        rifa.setCodigoPublico("OLD123");
        rifa.setOrganizadorId(12L);
        rifa.setCantidadBoletos(10);
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setMetodoSorteo(MetodoSorteo.LOTERIA);
        rifa.setLoteriaId(5L); // la migración V3 la enlazó por nombre, pero no tiene dígitos
        when(rifas.findById(3L)).thenReturn(Optional.of(rifa));
        Premio premio = new Premio();
        premio.setPosicion(1);
        premio.setNombre("Bono");
        when(premios.findByRifaIdOrderByPosicionAsc(3L)).thenReturn(List.of(premio));
        Boleto boleto = new Boleto();
        boleto.setNumero(4);
        boleto.setEstado(EstadoBoleto.VENDIDO);
        when(boletos.findByRifaIdAndEstadoOrderByNumeroAsc(3L, EstadoBoleto.VENDIDO)).thenReturn(List.of(boleto));

        var resultado = servicio.sortear(3L, new SorteoDtos.SorteoRequest(List.of(4)),
            new UsuarioAutenticado(12L, Rol.ORGANIZADOR));

        assertEquals(4, resultado.get(0).numeroBoleto());
        assertEquals(EstadoRifa.SORTEADA, rifa.getEstado());
    }
}
