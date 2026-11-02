package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alexrifas.api.dto.AdminDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminCompraServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);

    private CompraRepository compras;
    private BoletoRepository boletos;
    private RifaRepository rifas;
    private AntiabusoService antiabuso;
    private AuditoriaService auditoria;
    private AdminCompraService servicio;
    private Rifa rifa;

    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        boletos = mock(BoletoRepository.class);
        rifas = mock(RifaRepository.class);
        antiabuso = mock(AntiabusoService.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new AdminCompraService(compras, boletos, rifas, antiabuso, auditoria,
            Clock.fixed(AHORA, ZoneOffset.UTC));

        rifa = new Rifa();
        ReflectionTestUtils.setField(rifa, "id", 10L);
        rifa.setCodigoPublico("ABC123");
        rifa.setTitulo("Rifa de prueba");
        rifa.setEstado(EstadoRifa.PUBLICADA);
        rifa.setPrecioBoleto(new BigDecimal("10000"));
        rifa.setMoneda("COP");
        rifa.setCantidadBoletos(100);
        rifa.setCierreVentasEn(AHORA.plusSeconds(86400));
        rifa.setFechaSorteo(AHORA.plusSeconds(2 * 86400));
        when(rifas.findByCodigoPublico("ABC123")).thenReturn(Optional.of(rifa));
    }

    private Compra compra(long id, EstadoCompra estado) {
        Compra c = new Compra();
        ReflectionTestUtils.setField(c, "id", id);
        c.setCodigo("COD" + id);
        c.setRifaId(10L);
        c.setCompradorNombre("Ana Gómez");
        c.setCompradorTelefono("+573001234567");
        c.setEstado(estado);
        c.setSubtotal(new BigDecimal("20000"));
        c.setExpiraEn(AHORA.plusSeconds(600));
        c.setCreadaEn(AHORA.minusSeconds(60));
        c.setActualizadaEn(AHORA.minusSeconds(60));
        return c;
    }

    private Boleto boleto(int numero, EstadoBoleto estado, Long compraId) {
        Boleto b = new Boleto();
        b.setRifaId(10L);
        b.setNumero(numero);
        b.setEstado(estado);
        b.setCompraId(compraId);
        b.setReservadoHasta(estado == EstadoBoleto.RESERVADO ? AHORA.plusSeconds(600) : null);
        return b;
    }

    private void esperar403(Runnable accion) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(403, error.getStatusCode().value());
    }

    // ---- autorización ----

    @Test
    void soloUnAdministradorPuedeListarLiberarYVerElResumen() {
        for (UsuarioAutenticado actor : new UsuarioAutenticado[] {
                new UsuarioAutenticado(2L, Rol.ORGANIZADOR), new UsuarioAutenticado(3L, Rol.CLIENTE), null}) {
            esperar403(() -> servicio.listar(actor, null, null, null, 0, 20));
            esperar403(() -> servicio.liberar("COD1", null, actor));
            esperar403(() -> servicio.resumenRifa("ABC123", actor));
        }
        verifyNoInteractions(compras, boletos, antiabuso);
    }

    // ---- listar ----

    @Test
    @SuppressWarnings("unchecked")
    void listarPendientesOrdenaPorLasQueVencenPrimeroYArmaLasFilas() {
        Compra c1 = compra(1, EstadoCompra.PENDIENTE);
        when(compras.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c1)));
        when(boletos.findByCompraIdIn(anyCollection())).thenReturn(
            List.of(boleto(7, EstadoBoleto.RESERVADO, 1L), boleto(4, EstadoBoleto.RESERVADO, 1L)));
        when(rifas.findAllById(anyCollection())).thenReturn(List.of(rifa));

        var pagina = servicio.listar(ADMIN, EstadoCompra.PENDIENTE, "abc123", "300 123 4567", 0, 20);

        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(compras).findAll(any(Specification.class), captura.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "expiraEn"), captura.getValue().getSort());
        var fila = pagina.contenido().get(0);
        assertEquals("COD1", fila.codigo());
        assertEquals("ABC123", fila.codigoRifa());
        assertEquals("+573001234567", fila.compradorTelefono()); // el admin ve el teléfono completo
        assertEquals(List.of(4, 7), fila.numeros());
        assertEquals("COP", fila.moneda());
        assertNull(fila.usuarioId());
        assertEquals(1, pagina.totalElementos());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarSinEstadoOrdenaPorLasMasRecientesYAcotaElTamano() {
        when(compras.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        servicio.listar(ADMIN, null, null, null, -5, 5000);

        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(compras).findAll(any(Specification.class), captura.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "creadaEn"), captura.getValue().getSort());
        assertEquals(0, captura.getValue().getPageNumber());
        assertEquals(100, captura.getValue().getPageSize());
        verify(boletos, never()).findByCompraIdIn(anyCollection());
    }

    @Test
    void listarConRifaInexistenteDevuelve404YConTelefonoInvalidoDevuelve400() {
        when(rifas.findByCodigoPublico("NOPE")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> servicio.listar(ADMIN, null, "nope", null, 0, 20)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> servicio.listar(ADMIN, null, null, "12345", 0, 20)).getStatusCode().value());
    }

    // ---- liberar ----

    @Test
    void liberarUnaReservaPendienteCancelaLaCompraYDevuelveLosBoletos() {
        Compra c = compra(1, EstadoCompra.PENDIENTE);
        Boleto b4 = boleto(4, EstadoBoleto.RESERVADO, 1L);
        Boleto b7 = boleto(7, EstadoBoleto.RESERVADO, 1L);
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(c));
        when(boletos.findByCompraId(1L)).thenReturn(List.of(b4, b7));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(10L), anyCollection())).thenReturn(List.of(b4, b7));

        var respuesta = servicio.liberar("cod1", new AdminDtos.LiberacionRequest("Pago no llegó"), ADMIN);

        assertEquals(EstadoCompra.CANCELADA, c.getEstado());
        assertEquals(AHORA, c.getActualizadaEn());
        for (Boleto b : List.of(b4, b7)) {
            assertEquals(EstadoBoleto.DISPONIBLE, b.getEstado());
            assertNull(b.getCompraId());
            assertNull(b.getReservadoHasta());
        }
        assertEquals(2, respuesta.boletosLiberados());
        assertEquals(EstadoCompra.CANCELADA, respuesta.estado());
        verify(compras).save(c);
        verify(antiabuso).liberar("COD1");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void liberarDejaRastroDeAuditoriaConElMotivoYLosBoletosLiberados() {
        Compra c = compra(1, EstadoCompra.PENDIENTE);
        Boleto b4 = boleto(4, EstadoBoleto.RESERVADO, 1L);
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(c));
        when(boletos.findByCompraId(1L)).thenReturn(List.of(b4));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(10L), anyCollection())).thenReturn(List.of(b4));

        servicio.liberar("COD1", new AdminDtos.LiberacionRequest("Pago no llegó"), ADMIN);

        ArgumentCaptor<Map<String, Object>> detalles = ArgumentCaptor.forClass((Class) Map.class);
        verify(auditoria).registrar(eq(ADMIN), eq("compra"), eq("COD1"), eq("RESERVA_LIBERADA"), detalles.capture());
        assertEquals(1, detalles.getValue().get("boletosLiberados"));
        assertEquals("Pago no llegó", detalles.getValue().get("motivo"));
    }

    @Test
    void liberarSinMotivoAuditaConLaMarcaSinMotivo() {
        Compra c = compra(1, EstadoCompra.PENDIENTE);
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(c));
        when(boletos.findByCompraId(1L)).thenReturn(List.of());

        servicio.liberar("COD1", null, ADMIN);

        verify(auditoria).registrar(eq(ADMIN), eq("compra"), eq("COD1"), eq("RESERVA_LIBERADA"), any(Map.class));
    }

    @Test
    void liberarQueFallaNoDejaRastroDeUnaAccionQueNoOcurrio() {
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(compra(1, EstadoCompra.CONFIRMADA)));

        assertThrows(ResponseStatusException.class, () -> servicio.liberar("COD1", null, ADMIN));

        verifyNoInteractions(auditoria);
    }

    @Test
    void liberarNoLeQuitaUnNumeroQueYaTomoOtraCompra() {
        Compra c = compra(1, EstadoCompra.PENDIENTE);
        Boleto propio = boleto(4, EstadoBoleto.RESERVADO, 1L);
        Boleto deOtro = boleto(7, EstadoBoleto.RESERVADO, 2L); // la reserva venció y otro comprador lo tomó
        Boleto vendido = boleto(9, EstadoBoleto.VENDIDO, 3L);
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(c));
        when(boletos.findByCompraId(1L)).thenReturn(List.of(propio, deOtro, vendido));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(10L), anyCollection()))
            .thenReturn(List.of(propio, deOtro, vendido));

        var respuesta = servicio.liberar("COD1", null, ADMIN);

        assertEquals(1, respuesta.boletosLiberados());
        assertEquals(EstadoBoleto.DISPONIBLE, propio.getEstado());
        assertEquals(EstadoBoleto.RESERVADO, deOtro.getEstado());
        assertEquals(2L, deOtro.getCompraId());
        assertEquals(EstadoBoleto.VENDIDO, vendido.getEstado());
    }

    @Test
    void soloSeLiberanReservasPendientes() {
        for (EstadoCompra estado : new EstadoCompra[] {
                EstadoCompra.CONFIRMADA, EstadoCompra.EXPIRADA, EstadoCompra.CANCELADA}) {
            Compra c = compra(1, estado);
            when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(c));

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> servicio.liberar("COD1", null, ADMIN));

            assertEquals(409, error.getStatusCode().value());
            assertEquals(estado, c.getEstado()); // no cambió
        }
        verify(compras, never()).save(any());
        verifyNoInteractions(boletos, antiabuso);
    }

    @Test
    void liberarUnaCompraInexistenteDevuelve404() {
        when(compras.findByCodigoForUpdate("NOPE")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> servicio.liberar("nope", null, ADMIN)).getStatusCode().value());
    }

    // ---- resumen ----

    @Test
    void resumenCalculaPorcentajeRecaudoYContadores() {
        when(boletos.countByRifaIdAndEstado(10L, EstadoBoleto.VENDIDO)).thenReturn(25L);
        when(boletos.countByRifaIdAndEstadoAndReservadoHastaAfter(10L, EstadoBoleto.RESERVADO, AHORA)).thenReturn(5L);
        when(compras.sumarSubtotal(10L, EstadoCompra.CONFIRMADA)).thenReturn(new BigDecimal("250000"));
        when(compras.sumarPendientesVigentes(10L, AHORA)).thenReturn(new BigDecimal("50000"));
        CompraRepository.EstadoConteo pendientes = conteo(EstadoCompra.PENDIENTE, 3);
        CompraRepository.EstadoConteo confirmadas = conteo(EstadoCompra.CONFIRMADA, 10);
        when(compras.contarPorEstado(10L)).thenReturn(List.of(pendientes, confirmadas));

        var r = servicio.resumenRifa("abc123", ADMIN);

        assertEquals(25L, r.vendidos());
        assertEquals(5L, r.reservados());
        assertEquals(70L, r.disponibles());
        assertEquals(new BigDecimal("25.00"), r.porcentajeVendido());
        assertEquals(new BigDecimal("250000"), r.recaudoConfirmado());
        assertEquals(new BigDecimal("50000"), r.recaudoPendiente());
        assertEquals(new BigDecimal("1000000"), r.recaudoMaximo());
        assertEquals(3L, r.comprasPendientes());
        assertEquals(10L, r.comprasConfirmadas());
        assertEquals(0L, r.comprasExpiradas());
        assertEquals(0L, r.comprasCanceladas());
    }

    @Test
    void resumenDeUnaRifaSinVentasDevuelveCeros() {
        when(compras.contarPorEstado(10L)).thenReturn(List.of());

        var r = servicio.resumenRifa("ABC123", ADMIN);

        assertEquals(100L, r.disponibles());
        assertEquals(new BigDecimal("0.00"), r.porcentajeVendido());
        assertEquals(BigDecimal.ZERO, r.recaudoConfirmado());
        assertEquals(BigDecimal.ZERO, r.recaudoPendiente());
    }

    @Test
    void resumenDeRifaInexistenteDevuelve404() {
        when(rifas.findByCodigoPublico("NOPE")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> servicio.resumenRifa("nope", ADMIN)).getStatusCode().value());
    }

    private static CompraRepository.EstadoConteo conteo(EstadoCompra estado, long total) {
        CompraRepository.EstadoConteo c = mock(CompraRepository.EstadoConteo.class);
        when(c.getEstado()).thenReturn(estado);
        when(c.getTotal()).thenReturn(total);
        return c;
    }
}
