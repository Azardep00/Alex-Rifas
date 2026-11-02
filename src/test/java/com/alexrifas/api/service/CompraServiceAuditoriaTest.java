package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.PagoWompiRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Auditoría de dinero (pendiente de CP-06): confirmar un pago manual deja rastro; si falla, no. */
class CompraServiceAuditoriaTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);

    private CompraRepository compras;
    private BoletoRepository boletos;
    private RifaRepository rifas;
    private PagoWompiRepository pagos;
    private AuditoriaService auditoria;
    private CompraService servicio;
    private Compra compra;

    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        boletos = mock(BoletoRepository.class);
        rifas = mock(RifaRepository.class);
        pagos = mock(PagoWompiRepository.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new CompraService(compras, boletos, rifas, pagos, mock(AntiabusoService.class), auditoria,
            Clock.fixed(AHORA, ZoneOffset.UTC));

        Rifa rifa = new Rifa();
        ReflectionTestUtils.setField(rifa, "id", 10L);
        rifa.setCodigoPublico("ABC123");
        rifa.setMoneda("COP");
        when(rifas.findById(10L)).thenReturn(Optional.of(rifa));

        compra = new Compra();
        ReflectionTestUtils.setField(compra, "id", 1L);
        compra.setCodigo("COD1");
        compra.setRifaId(10L);
        compra.setEstado(EstadoCompra.PENDIENTE);
        compra.setSubtotal(new BigDecimal("20000"));
        compra.setExpiraEn(AHORA.plusSeconds(600));
        when(compras.findByCodigoForUpdate("COD1")).thenReturn(Optional.of(compra));
    }

    private Boleto reservado(int numero) {
        Boleto b = new Boleto();
        b.setRifaId(10L);
        b.setNumero(numero);
        b.setEstado(EstadoBoleto.RESERVADO);
        b.setCompraId(1L);
        b.setReservadoHasta(AHORA.plusSeconds(600));
        return b;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void confirmarUnPagoManualDejaRastroConMontoBoletosMetodoYReferencia() {
        Boleto b4 = reservado(4);
        Boleto b7 = reservado(7);
        when(boletos.findByCompraId(1L)).thenReturn(List.of(b4, b7));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(10L), anyCollection())).thenReturn(List.of(b4, b7));

        var respuesta = servicio.confirmarPagoManual("COD1", new CompraDtos.ConfirmacionManualRequest("Nequi", "Comprobante OK"), ADMIN);

        assertEquals(EstadoCompra.CONFIRMADA, respuesta.estado());
        assertEquals(EstadoBoleto.VENDIDO, b4.getEstado());
        ArgumentCaptor<Map<String, Object>> detalles = ArgumentCaptor.forClass((Class) Map.class);
        verify(auditoria).registrar(eq(ADMIN), eq("compra"), eq("COD1"), eq("PAGO_CONFIRMADO_MANUAL"), detalles.capture());
        assertEquals("ABC123", detalles.getValue().get("rifa"));
        assertEquals(new BigDecimal("20000"), detalles.getValue().get("monto"));
        assertEquals(List.of(4, 7), detalles.getValue().get("boletos"));
        assertEquals("Nequi", detalles.getValue().get("metodo"));
        assertEquals("MANUAL-COD1", detalles.getValue().get("referencia"));
    }

    @Test
    void siLaReservaYaNoEsValidaNoSeAudita() {
        Boleto vendidoPorOtro = reservado(4);
        vendidoPorOtro.setEstado(EstadoBoleto.DISPONIBLE);
        when(boletos.findByCompraId(1L)).thenReturn(List.of(reservado(4)));
        when(boletos.findByRifaIdAndNumeroInForUpdate(eq(10L), anyCollection())).thenReturn(List.of(vendidoPorOtro));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.confirmarPagoManual("COD1", new CompraDtos.ConfirmacionManualRequest("Nequi", null), ADMIN));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(auditoria);
        verify(pagos, never()).save(any());
    }

    @Test
    void siLaCompraYaNoEstaPendienteNoSeAudita() {
        compra.setEstado(EstadoCompra.CONFIRMADA);

        assertThrows(ResponseStatusException.class,
            () -> servicio.confirmarPagoManual("COD1", new CompraDtos.ConfirmacionManualRequest("Nequi", null), ADMIN));

        verifyNoInteractions(auditoria);
    }

    @Test
    void unNoAdministradorNoPuedeConfirmarNiDejaRastro() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> servicio.confirmarPagoManual("COD1", new CompraDtos.ConfirmacionManualRequest("Nequi", null),
                new UsuarioAutenticado(2L, Rol.ORGANIZADOR)));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(auditoria);
    }
}
