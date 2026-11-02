package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.PagoWompiRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.model.Rol;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ReservaPublicaServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-22T12:00:00Z");
    private static final String IP = "203.0.113.9";

    private CompraRepository compras;
    private BoletoRepository boletos;
    private RifaRepository rifas;
    private AntiabusoService antiabuso;
    private CompraService servicio;
    private Rifa rifa;

    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        boletos = mock(BoletoRepository.class);
        rifas = mock(RifaRepository.class);
        antiabuso = mock(AntiabusoService.class);
        servicio = new CompraService(compras, boletos, rifas, mock(PagoWompiRepository.class), antiabuso,
            mock(AuditoriaService.class), Clock.fixed(AHORA, ZoneOffset.UTC));

        rifa = new Rifa();
        ReflectionTestUtils.setField(rifa, "id", 1L);
        rifa.setCodigoPublico("ABC123");
        rifa.setEstado(EstadoRifa.PUBLICADA);
        rifa.setPrecioBoleto(new BigDecimal("10000"));
        rifa.setMoneda("COP");
        rifa.setMaximoBoletosPorCompra(3);
        rifa.setReservaMinutos(60);
        rifa.setCierreVentasEn(AHORA.plusSeconds(7 * 24 * 3600));
        when(rifas.findByCodigoPublico("ABC123")).thenReturn(Optional.of(rifa));
    }

    private Boleto boleto(int numero, EstadoBoleto estado) {
        Boleto b = new Boleto();
        b.setRifaId(1L);
        b.setNumero(numero);
        b.setEstado(estado);
        return b;
    }

    private CompraDtos.ReservaPublicaRequest request(String telefono, Integer... numeros) {
        return new CompraDtos.ReservaPublicaRequest("abc123", "  Ana   Gómez ", telefono, List.of(numeros));
    }

    @Test
    void reservaDeInvitadoGuardaNombreTelefonoYUsaLosMinutosDeLaRifa() {
        Boleto b4 = boleto(4, EstadoBoleto.DISPONIBLE);
        Boleto b7 = boleto(7, EstadoBoleto.DISPONIBLE);
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection())).thenReturn(List.of(b4, b7));

        var respuesta = servicio.reservarInvitado(request("300 123 4567", 4, 7), IP);

        ArgumentCaptor<Compra> captura = ArgumentCaptor.forClass(Compra.class);
        verify(compras).saveAndFlush(captura.capture());
        Compra compra = captura.getValue();
        assertNull(compra.getUsuarioId());
        assertEquals("Ana Gómez", compra.getCompradorNombre());
        assertEquals("+573001234567", compra.getCompradorTelefono());
        assertEquals(EstadoCompra.PENDIENTE, compra.getEstado());
        assertEquals(AHORA.plusSeconds(60 * 60), compra.getExpiraEn());
        assertEquals(new BigDecimal("20000"), compra.getSubtotal());
        assertEquals(EstadoBoleto.RESERVADO, b4.getEstado());
        assertEquals(EstadoBoleto.RESERVADO, b7.getEstado());
        assertEquals(List.of(4, 7), respuesta.numeros());
        assertEquals("+57 300 ••• 4567", respuesta.telefonoEnmascarado());
    }

    @Test
    void numeroOcupadoDevuelve409YNoCreaLaCompra() {
        Boleto vendido = boleto(4, EstadoBoleto.VENDIDO);
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection())).thenReturn(List.of(vendido));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 4), IP));

        assertEquals(409, error.getStatusCode().value());
        verify(compras, never()).saveAndFlush(any());
    }

    @Test
    void reservaVencidaSeLiberaYPuedeReservarseDeNuevo() {
        Boleto vencido = boleto(4, EstadoBoleto.RESERVADO);
        vencido.setCompraId(99L);
        vencido.setReservadoHasta(AHORA.minusSeconds(1));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection())).thenReturn(List.of(vencido));

        servicio.reservarInvitado(request("3001234567", 4), IP);

        assertEquals(EstadoBoleto.RESERVADO, vencido.getEstado());
        verify(compras).saveAndFlush(any(Compra.class));
    }

    @Test
    void superarElMaximoPorCompraDevuelve400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 1, 2, 3, 4), IP));
        assertEquals(400, error.getStatusCode().value());
        verify(boletos, never()).findByRifaIdAndNumeroInForUpdate(any(), anyCollection());
    }

    @Test
    void rifaNoPublicadaDevuelve409() {
        rifa.setEstado(EstadoRifa.CERRADA);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 4), IP));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void telefonoInvalidoSeRechazaAntesDeTocarLaBaseDeDatos() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("12345", 4), IP));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(compras, boletos, rifas);
    }

    @Test
    void consultaConTelefonoIncorrectoDevuelve404() {
        when(compras.findByCodigoAndCompradorTelefono("COD1", "+573009998888")).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.consultarInvitado("cod1", "300 999 8888"));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void consultaConCodigoYTelefonoCorrectosDevuelveLaReserva() {
        Compra compra = new Compra();
        ReflectionTestUtils.setField(compra, "id", 5L);
        compra.setCodigo("COD1");
        compra.setRifaId(1L);
        compra.setCompradorNombre("Ana Gómez");
        compra.setCompradorTelefono("+573001234567");
        compra.setEstado(EstadoCompra.PENDIENTE);
        compra.setSubtotal(new BigDecimal("10000"));
        compra.setExpiraEn(AHORA.plusSeconds(600));
        compra.setCreadaEn(AHORA);
        compra.setActualizadaEn(AHORA);
        Boleto b = boleto(7, EstadoBoleto.RESERVADO);
        when(compras.findByCodigoAndCompradorTelefono("COD1", "+573001234567")).thenReturn(Optional.of(compra));
        when(rifas.findById(1L)).thenReturn(Optional.of(rifa));
        when(boletos.findByCompraId(5L)).thenReturn(List.of(b));

        var respuesta = servicio.consultarInvitado("cod1", "+57 300 123 4567");

        assertEquals("COD1", respuesta.codigo());
        assertEquals("ABC123", respuesta.codigoRifa());
        assertEquals(List.of(7), respuesta.numeros());
        assertEquals("Ana Gómez", respuesta.compradorNombre());
    }

    // ---- CP-05: antiabuso ----

    @Test
    void laReservaDeInvitadoPasaPorElAntiabusoConTelefonoNormalizadoIpYVigencia() {
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection()))
            .thenReturn(List.of(boleto(4, EstadoBoleto.DISPONIBLE)));

        servicio.reservarInvitado(request("300 123 4567", 4), IP);

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(antiabuso).verificarYRegistrar(codigo.capture(), eq("+573001234567"), eq(IP),
            eq(AHORA), eq(AHORA.plusSeconds(60 * 60)));
        ArgumentCaptor<Compra> compra = ArgumentCaptor.forClass(Compra.class);
        verify(compras).saveAndFlush(compra.capture());
        assertEquals(codigo.getValue(), compra.getValue().getCodigo());
        verify(antiabuso, never()).liberar(anyString());
    }

    @Test
    void siSeAlcanzaElTopeDevuelve429Sin_bloquearNiTocarBoletos() {
        doThrow(new ReservasPendientesExcedidasException("Ya tienes 3 reservas pendientes de pago.", 600))
            .when(antiabuso).verificarYRegistrar(anyString(), eq("+573001234567"), eq(IP), any(), any());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 4), IP));

        assertEquals(429, error.getStatusCode().value());
        verify(boletos, never()).findByRifaIdAndNumeroInForUpdate(any(), anyCollection());
        verify(compras, never()).saveAndFlush(any());
        verify(antiabuso, never()).liberar(anyString());
    }

    @Test
    void siLaReservaFallaSeDevuelveElCupoPorIp() {
        Boleto vendido = boleto(4, EstadoBoleto.VENDIDO);
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection())).thenReturn(List.of(vendido));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 4), IP));

        assertEquals(409, error.getStatusCode().value());
        ArgumentCaptor<String> registrado = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> liberado = ArgumentCaptor.forClass(String.class);
        verify(antiabuso).verificarYRegistrar(registrado.capture(), anyString(), anyString(), any(), any());
        verify(antiabuso).liberar(liberado.capture());
        assertEquals(registrado.getValue(), liberado.getValue());
    }

    @Test
    void unaSolicitudInvalidaNoConsumeCupo() {
        assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("3001234567", 1, 2, 3, 4), IP)); // supera el máximo por compra
        assertThrows(ResponseStatusException.class,
            () -> servicio.reservarInvitado(request("12345", 4), IP));              // teléfono inválido
        verifyNoInteractions(antiabuso);
    }

    @Test
    void laReservaConCuentaNoPasaPorElAntiabusoDeInvitados() {
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(1L), anyCollection()))
            .thenReturn(List.of(boleto(4, EstadoBoleto.DISPONIBLE)));

        servicio.reservar(new CompraDtos.ReservaRequest("abc123", List.of(4)), new UsuarioAutenticado(7L, Rol.CLIENTE));

        verifyNoInteractions(antiabuso);
    }
}
