package com.alexrifas.api.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alexrifas.api.config.AppProperties;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-secret-for-jwt-that-is-at-least-32-bytes";

    @Test
    void emiteYValidaTokenConIdYRol() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(new AppProperties(new AppProperties.Seguridad(SECRET, 15, 7)), clock);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(42L);
        when(usuario.getRol()).thenReturn(Rol.ORGANIZADOR);

        String token = service.generarAccessToken(usuario);
        UsuarioAutenticado autenticado = service.validar(token);

        assertEquals(Long.valueOf(42L), autenticado.id());
        assertEquals(Rol.ORGANIZADOR, autenticado.rol());
        assertEquals(900L, service.getExpiracionSegundos());
    }

    @Test
    void rechazaTokenManipulado() {
        Clock clock = Clock.systemUTC();
        JwtService service = new JwtService(new AppProperties(new AppProperties.Seguridad(SECRET, 15, 7)), clock);
        assertThrows(JwtException.class, () -> service.validar("esto.no.es.un.jwt"));
    }
}
