package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alexrifas.api.config.AntiabusoProperties;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.repository.CompraRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class AntiabusoServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant VENCE = AHORA.plus(Duration.ofMinutes(60));
    private static final String TEL = "+573001234567";
    private static final String IP = "203.0.113.9";

    private CompraRepository compras;
    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private ValueOperations<String, String> valores;
    private AntiabusoService servicio;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(redis.opsForValue()).thenReturn(valores);
        when(compras.findByCompradorTelefonoAndEstadoAndExpiraEnAfterOrderByExpiraEnAsc(
            eq(TEL), eq(EstadoCompra.PENDIENTE), any())).thenReturn(List.of());
        when(zset.size("abuso:ip:" + IP)).thenReturn(1L);
        servicio = new AntiabusoService(compras, redis, new AntiabusoProperties(3, 5, 1));
    }

    private List<Compra> pendientes(int cuantas, Instant primeraVence) {
        List<Compra> lista = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            Compra c = new Compra();
            c.setExpiraEn(primeraVence.plusSeconds(i * 60L));
            lista.add(c);
        }
        return lista;
    }

    private void telefonoConPendientes(int cuantas, Instant primeraVence) {
        when(compras.findByCompradorTelefonoAndEstadoAndExpiraEnAfterOrderByExpiraEnAsc(
            TEL, EstadoCompra.PENDIENTE, AHORA)).thenReturn(pendientes(cuantas, primeraVence));
    }

    @Test
    void bajoElTopeRegistraLaReservaPorIp() {
        telefonoConPendientes(2, AHORA.plusSeconds(300));

        servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE);

        verify(zset).removeRangeByScore("abuso:ip:" + IP, Double.NEGATIVE_INFINITY, AHORA.toEpochMilli());
        verify(zset).add("abuso:ip:" + IP, "COD1", (double) VENCE.toEpochMilli());
        verify(valores).set(eq("abuso:compra:COD1"), eq(IP), any(Duration.class));
    }

    @Test
    void conTresPendientesElTelefonoRecibe429ConRetryAfterHastaElPrimerVencimiento() {
        telefonoConPendientes(3, AHORA.plusSeconds(600));

        ReservasPendientesExcedidasException error = assertThrows(ReservasPendientesExcedidasException.class,
            () -> servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE));

        assertEquals(429, error.getStatusCode().value());
        assertEquals(600, error.getRetryAfterSegundos());
        assertTrue(error.getReason().contains("3 reservas pendientes"));
        assertTrue(error.getReason().contains("10 minutos"));
        verifyNoInteractions(redis); // el tope por teléfono se evalúa antes y no consume cupo por IP
    }

    @Test
    void justoEnElTopeDeIpTodaviaPasa() {
        when(zset.size("abuso:ip:" + IP)).thenReturn(5L);
        assertDoesNotThrow(() -> servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE));
        verify(zset, never()).remove(anyString(), any());
    }

    @Test
    void pasadoElTopeDeIpRecibe429YDevuelveElCupoRecienTomado() {
        when(zset.size("abuso:ip:" + IP)).thenReturn(6L);
        ZSetOperations.TypedTuple<String> primero = mock(ZSetOperations.TypedTuple.class);
        when(primero.getScore()).thenReturn((double) AHORA.plusSeconds(120).toEpochMilli());
        Set<ZSetOperations.TypedTuple<String>> conjunto = new LinkedHashSet<>(List.of(primero));
        when(zset.rangeWithScores("abuso:ip:" + IP, 0, 0)).thenReturn(conjunto);

        ReservasPendientesExcedidasException error = assertThrows(ReservasPendientesExcedidasException.class,
            () -> servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE));

        assertEquals(429, error.getStatusCode().value());
        assertEquals(120, error.getRetryAfterSegundos());
        verify(zset).remove("abuso:ip:" + IP, "COD1");
        verify(valores, never()).set(eq("abuso:compra:COD1"), anyString(), any(Duration.class));
    }

    @Test
    void siRedisFallaElTopePorIpSeOmiteYLaReservaSigue() {
        when(redis.opsForZSet()).thenThrow(new IllegalStateException("redis caído"));
        assertDoesNotThrow(() -> servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE));
    }

    @Test
    void siRedisFallaElTopePorTelefonoSigueFuncionando() {
        when(redis.opsForZSet()).thenThrow(new IllegalStateException("redis caído"));
        telefonoConPendientes(3, AHORA.plusSeconds(60));
        assertThrows(ReservasPendientesExcedidasException.class,
            () -> servicio.verificarYRegistrar("COD1", TEL, IP, AHORA, VENCE));
    }

    @Test
    void sinIpNoSeConsultaRedis() {
        servicio.verificarYRegistrar("COD1", TEL, null, AHORA, VENCE);
        verifyNoInteractions(redis);
    }

    @Test
    void liberarQuitaLaReservaDelConjuntoDeSuIp() {
        when(valores.getAndDelete("abuso:compra:COD1")).thenReturn(IP);
        servicio.liberar("COD1");
        verify(zset).remove("abuso:ip:" + IP, "COD1");
    }

    @Test
    void liberarUnaReservaDesconocidaOConRedisCaidoNoLanza() {
        when(valores.getAndDelete("abuso:compra:NOPE")).thenReturn(null);
        assertDoesNotThrow(() -> servicio.liberar("NOPE"));
        verify(zset, never()).remove(anyString(), any());

        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis caído"));
        assertDoesNotThrow(() -> servicio.liberar("COD1"));
    }
}
