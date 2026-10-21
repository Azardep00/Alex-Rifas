package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alexrifas.api.config.AppProperties;
import com.alexrifas.api.dto.AuthDtos;
import com.alexrifas.api.entity.RefreshToken;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.RefreshTokenRepository;
import com.alexrifas.api.repository.UsuarioRepository;
import com.alexrifas.api.security.JwtService;
import com.alexrifas.api.security.TokenUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        var properties = new AppProperties(new AppProperties.Seguridad(
            "test-secret-for-jwt-that-is-at-least-32-bytes", 15, 7));
        service = new AuthService(usuarios, refreshTokens, passwordEncoder, jwtService, properties,
            Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registroNormalizaCorreoYAsignaRolCliente() {
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));
        when(jwtService.generarAccessToken(any(Usuario.class))).thenReturn("access-token-test");
        when(jwtService.getExpiracionSegundos()).thenReturn(900L);
        when(passwordEncoder.encode("UnaClaveSegura123")).thenReturn("bcrypt-hash");

        AuthDtos.AuthResponse response = service.registrar(new AuthDtos.RegistroRequest(
            "Ana", "Pérez", "3001234567", " ANA@example.com ", "UnaClaveSegura123",
            LocalDate.of(2000, 4, 12), true));

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(usuarioCaptor.capture());
        Usuario creado = usuarioCaptor.getValue();
        assertEquals("ana@example.com", creado.getCorreo());
        assertEquals("bcrypt-hash", creado.getContrasenaHash());
        assertEquals(Rol.CLIENTE, creado.getRol());
        assertTrue(creado.isActivo());
        assertEquals("Bearer", response.tipo());

        ArgumentCaptor<RefreshToken> refreshCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(refreshCaptor.capture());
        assertEquals(TokenUtils.sha256(response.refreshToken()), refreshCaptor.getValue().getTokenHash());
        assertNotEquals(response.refreshToken(), refreshCaptor.getValue().getTokenHash());
    }

    @Test
    void registroRechazaCorreoDuplicado() {
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.registrar(
            new AuthDtos.RegistroRequest("Ana", "Pérez", "3001234567", "ana@example.com",
                "UnaClaveSegura123", LocalDate.of(2000, 4, 12), true)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(usuarios, never()).save(any(Usuario.class));
    }
}
