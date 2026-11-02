package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.ReglaVacante;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.LoteriaRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RifaServiceTest {
    @Mock RifaRepository rifas;
    @Mock PremioRepository premios;
    @Mock BoletoRepository boletos;
    @Mock UsuarioRepository usuarios;
    @Mock LoteriaRepository loterias;
    private RifaService service;

    @BeforeEach
    void setUp() {
        service = new RifaService(rifas, premios, boletos, usuarios, loterias,
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
            5, com.alexrifas.api.model.MetodoSorteo.ALEATORIO, null, null, null, null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crear(request, new UsuarioAutenticado(1L, Rol.ADMIN)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(rifas, never()).save(any());
        verify(boletos, never()).saveAll(any());
    }

    // ---- CP-09: configuración de sorteo por lotería ----

    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, Rol.ADMIN);

    private static RifaDtos.RifaRequest peticion(MetodoSorteo metodo, int cantidad, Long loteriaId, Integer digitos,
                                                 ReglaVacante regla, Integer reservaMinutos) {
        return new RifaDtos.RifaRequest("Rifa de prueba", "Descripción", null, new BigDecimal("1000.00"), "COP",
            cantidad, Instant.parse("2026-12-20T18:00:00Z"), Instant.parse("2026-12-19T18:00:00Z"), 5, metodo,
            "texto libre", null, null, loteriaId, digitos, regla, reservaMinutos);
    }

    private Loteria loteria(boolean activa) {
        Loteria l = new Loteria();
        ReflectionTestUtils.setField(l, "id", 5L);
        l.setNombre("Lotería de Bogotá");
        l.setActiva(activa);
        return l;
    }

    private Rifa crearYCapturar(RifaDtos.RifaRequest request) {
        when(rifas.save(any(Rifa.class))).thenAnswer(inv -> inv.getArgument(0));
        service.crear(request, ADMIN);
        ArgumentCaptor<Rifa> guardada = ArgumentCaptor.forClass(Rifa.class);
        verify(rifas).save(guardada.capture());
        return guardada.getValue();
    }

    private int rechazo(RifaDtos.RifaRequest request) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.crear(request, ADMIN));
        verify(rifas, never()).save(any());
        verify(boletos, never()).saveAll(any());
        return ex.getStatusCode().value();
    }

    @Test
    void guardaLaConfiguracionDeLoteriaCompleta() {
        when(loterias.findById(5L)).thenReturn(Optional.of(loteria(true)));

        Rifa rifa = crearYCapturar(peticion(MetodoSorteo.LOTERIA, 100, 5L, 2, ReglaVacante.ADJUDICAR_SIGUIENTE_VENDIDO, 60));

        assertEquals(5L, rifa.getLoteriaId());
        assertEquals(2, rifa.getDigitos());
        assertEquals(ReglaVacante.ADJUDICAR_SIGUIENTE_VENDIDO, rifa.getReglaVacante());
        assertEquals(60, rifa.getReservaMinutos());
        assertEquals("Lotería de Bogotá", rifa.getLoteriaNombre()); // manda el nombre del catálogo, no el texto libre
        assertTrue(rifa.sorteaPorLoteria());
    }

    @Test
    void sinConfiguracionExtraUsaLosValoresPorDefecto() {
        Rifa rifa = crearYCapturar(peticion(MetodoSorteo.ALEATORIO, 100, null, null, null, null));

        assertNull(rifa.getLoteriaId());
        assertNull(rifa.getDigitos());
        assertEquals(ReglaVacante.REPETIR_SIGUIENTE_LOTERIA, rifa.getReglaVacante());
        assertEquals(15, rifa.getReservaMinutos());
        assertFalse(rifa.sorteaPorLoteria());
    }

    @Test
    void conLoteriaSonObligatoriosLaLoteriaYLosDigitos() {
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100, null, 2, null, null)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100, 5L, null, null, null)));
    }

    @Test
    void losDigitosSoloPuedenSer2o3o4() {
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100, 5L, 1, null, null)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100_000, 5L, 5, null, null)));
    }

    @Test
    void laCantidadDeBoletosDebeSerExactamenteDiezALosDigitos() {
        when(loterias.findById(5L)).thenReturn(Optional.of(loteria(true))); // así solo falla la cantidad
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 500, 5L, 2, null, null)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100, 5L, 3, null, null)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 1000, 5L, 4, null, null)));
    }

    @Test
    void aceptaLasTresCombinacionesValidas() {
        when(loterias.findById(5L)).thenReturn(Optional.of(loteria(true)));
        assertEquals(1000, crearYCapturar(peticion(MetodoSorteo.LOTERIA, 1000, 5L, 3, null, null)).getCantidadBoletos());
    }

    @Test
    void laLoteriaDebeExistirYEstarActiva() {
        when(loterias.findById(5L)).thenReturn(Optional.empty());
        assertEquals(400, rechazo(peticion(MetodoSorteo.LOTERIA, 100, 5L, 2, null, null)));

        when(loterias.findById(5L)).thenReturn(Optional.of(loteria(false)));
        assertEquals(409, rechazo(peticion(MetodoSorteo.LOTERIA, 100, 5L, 2, null, null)));
    }

    @Test
    void loteriaYDigitosNoAplicanSiElMetodoNoEsLoteria() {
        assertEquals(400, rechazo(peticion(MetodoSorteo.ALEATORIO, 100, 5L, null, null, null)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.MANUAL, 100, null, 2, null, null)));
    }

    @Test
    void laReservaDebeDurarEntre5Y1440Minutos() {
        assertEquals(400, rechazo(peticion(MetodoSorteo.ALEATORIO, 100, null, null, null, 4)));
        assertEquals(400, rechazo(peticion(MetodoSorteo.ALEATORIO, 100, null, null, null, 1441)));
        assertEquals(5, crearYCapturar(peticion(MetodoSorteo.ALEATORIO, 100, null, null, null, 5)).getReservaMinutos());
    }
}
