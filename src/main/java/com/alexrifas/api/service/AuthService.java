package com.alexrifas.api.service;

import com.alexrifas.api.config.AppProperties;
import com.alexrifas.api.dto.AuthDtos;
import com.alexrifas.api.entity.RefreshToken;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.RefreshTokenRepository;
import com.alexrifas.api.repository.UsuarioRepository;
import com.alexrifas.api.security.JwtService;
import com.alexrifas.api.security.TokenUtils;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UsuarioRepository usuarioRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties properties, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthDtos.AuthResponse registrar(AuthDtos.RegistroRequest request) {
        String correo = normalizarCorreo(request.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
        validarPassword(request.contrasena());
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        usuario.setTelefono(request.telefono().trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        usuario.setRol(Rol.CLIENTE); // El cliente nunca puede elegir su propio rol.
        usuario.setActivo(true);
        usuario.setFechaNacimiento(request.fechaNacimiento());
        usuario.setAceptoTerminosEn(Instant.now(clock));
        usuario.setCreadoEn(Instant.now(clock));
        return crearSesion(usuarioRepository.save(usuario));
    }

    @Transactional
    public AuthDtos.UsuarioDto crearOrganizador(AuthDtos.CrearOrganizadorRequest request) {
        String correo = normalizarCorreo(request.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
        validarPassword(request.contrasena());
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        usuario.setTelefono(request.telefono().trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        usuario.setRol(Rol.ORGANIZADOR);
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now(clock));
        return AuthDtos.UsuarioDto.de(usuarioRepository.save(usuario));
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        if (request.contrasena().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña inválidos.");
        }
        String correo = normalizarCorreo(request.correo());
        Usuario usuario = usuarioRepository.findByCorreo(correo)
            .filter(Usuario::isActivo)
            .filter(u -> passwordEncoder.matches(request.contrasena(), u.getContrasenaHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña inválidos."));
        return crearSesion(usuario);
    }

    @Transactional(noRollbackFor = RefreshTokenReutilizadoException.class)
    public AuthDtos.AuthResponse renovar(String tokenPlano) {
        String hash = TokenUtils.sha256(tokenPlano);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido."));
        if (token.isRevocado()) {
            // A refresh token that was already rotated is a possible replay: invalidate all sessions.
            refreshTokenRepository.revocarTodos(token.getUsuarioId());
            throw new RefreshTokenReutilizadoException();
        }
        if (!token.getExpiraEn().isAfter(Instant.now(clock))) {
            refreshTokenRepository.revocarUno(token.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        if (refreshTokenRepository.revocarUno(token.getId()) != 1) {
            refreshTokenRepository.revocarTodos(token.getUsuarioId());
            throw new RefreshTokenReutilizadoException();
        }
        Usuario usuario = usuarioRepository.findById(token.getUsuarioId())
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no está activa."));
        return crearSesion(usuario);
    }

    @Transactional
    public void cerrarSesion(String tokenPlano) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256(tokenPlano))
            .filter(token -> !token.isRevocado())
            .ifPresent(token -> refreshTokenRepository.revocarUno(token.getId()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UsuarioDto usuarioActual(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no está activa."));
        return AuthDtos.UsuarioDto.de(usuario);
    }

    private AuthDtos.AuthResponse crearSesion(Usuario usuario) {
        String access = jwtService.generarAccessToken(usuario);
        String refreshPlano = nuevoRefreshToken();
        Instant ahora = Instant.now(clock);
        RefreshToken refresh = new RefreshToken();
        refresh.setTokenHash(TokenUtils.sha256(refreshPlano));
        refresh.setUsuarioId(usuario.getId());
        refresh.setCreadoEn(ahora);
        refresh.setExpiraEn(ahora.plusSeconds(properties.seguridad().refreshExpiracionDias() * 24 * 60 * 60));
        refresh.setRevocado(false);
        refreshTokenRepository.save(refresh);
        return new AuthDtos.AuthResponse(access, refreshPlano, "Bearer", jwtService.getExpiracionSegundos(),
            AuthDtos.UsuarioDto.de(usuario));
    }

    private String nuevoRefreshToken() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private void validarPassword(String password) {
        if (password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La contraseña debe tener al menos 10 caracteres y como máximo 72 bytes UTF-8.");
        }
    }
}
