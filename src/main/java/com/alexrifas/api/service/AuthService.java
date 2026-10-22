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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registro, inicio de sesión y manejo de sesiones. El access token (JWT) dura pocos minutos; la sesión larga
 * se mantiene con un refresh token aleatorio que se guarda solo como hash, se rota en cada uso y se revoca
 * en el cierre de sesión. Si se reutiliza uno ya rotado, se cierran todas las sesiones del usuario.
 */
@Service
public class AuthService {
    private static final String TIPO_TOKEN = "Bearer";
    private static final int EDAD_MINIMA = 18;
    private static final int MAX_BYTES_CONTRASENA = 72; // límite de BCrypt
    /** Hash BCrypt (coste 12) de una clave aleatoria descartada: iguala el tiempo de respuesta si el correo no existe. */
    private static final String HASH_FALSO = "$2b$12$AyL1ny7azf7miF7hj6J1quPZhiy6P06u.T0NI1ypoWBWVkP6M3khq";

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties properties, Clock clock) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Todo registro público crea un CLIENTE; los demás roles los asigna un administrador. */
    @Transactional
    public AuthDtos.AuthResponse registrar(AuthDtos.RegistroRequest request) {
        if (!request.aceptoTerminos()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes aceptar los términos y condiciones.");
        }
        Instant ahora = Instant.now(clock);
        LocalDate hoy = LocalDate.now(clock);
        if (request.fechaNacimiento() == null || request.fechaNacimiento().isAfter(hoy.minusYears(EDAD_MINIMA))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes ser mayor de edad para registrarte.");
        }
        if (request.contrasena() == null
                || request.contrasena().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La contraseña no puede superar " + MAX_BYTES_CONTRASENA + " bytes.");
        }
        String correo = normalizarCorreo(request.correo());
        if (usuarios.existsByCorreo(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        usuario.setTelefono(request.telefono().trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        usuario.setRol(Rol.CLIENTE);
        usuario.setActivo(true);
        usuario.setFechaNacimiento(request.fechaNacimiento());
        usuario.setAceptoTerminosEn(ahora);
        usuario.setCreadoEn(ahora);
        Usuario guardado = usuarios.save(usuario);
        return emitirSesion(guardado, ahora);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String correo = normalizarCorreo(request.correo());
        Usuario usuario = usuarios.findByCorreo(correo).orElse(null);
        String hash = usuario == null ? HASH_FALSO : usuario.getContrasenaHash();
        boolean claveValida = request.contrasena().getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES_CONTRASENA
            && passwordEncoder.matches(request.contrasena(), hash);
        // Mismo mensaje para correo inexistente, clave errónea o cuenta inactiva: no revela cuál falló.
        if (usuario == null || !claveValida || !usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        }
        return emitirSesion(usuario, Instant.now(clock));
    }

    /**
     * Canjea un refresh token por un par nuevo (rotación). La reutilización de un token ya revocado revoca todas
     * las sesiones del usuario; por eso esa excepción no deshace la transacción.
     */
    @Transactional(noRollbackFor = RefreshTokenReutilizadoException.class)
    public AuthDtos.AuthResponse refrescar(AuthDtos.RefreshRequest request) {
        String hash = TokenUtils.sha256(request.refreshToken());
        RefreshToken actual = refreshTokens.findByTokenHashForUpdate(hash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "La sesión no es válida. Inicia sesión de nuevo."));
        if (actual.isRevocado()) {
            refreshTokens.revocarTodosDelUsuario(actual.getUsuarioId());
            throw new RefreshTokenReutilizadoException();
        }
        Instant ahora = Instant.now(clock);
        if (!actual.getExpiraEn().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión venció. Inicia sesión de nuevo.");
        }
        Usuario usuario = usuarios.findById(actual.getUsuarioId())
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "La sesión no es válida. Inicia sesión de nuevo."));
        actual.setRevocado(true);
        refreshTokens.save(actual);
        return emitirSesion(usuario, ahora);
    }

    /** Idempotente: si el token no existe o ya estaba revocado, responde igual para no dar pistas. */
    @Transactional
    public void cerrarSesion(AuthDtos.RefreshRequest request) {
        refreshTokens.findByTokenHash(TokenUtils.sha256(request.refreshToken())).ifPresent(token -> {
            if (!token.isRevocado()) {
                token.setRevocado(true);
                refreshTokens.save(token);
            }
        });
    }

    private AuthDtos.AuthResponse emitirSesion(Usuario usuario, Instant ahora) {
        String accessToken = jwtService.generarAccessToken(usuario);
        String refreshToken = generarRefreshToken();

        RefreshToken registro = new RefreshToken();
        registro.setTokenHash(TokenUtils.sha256(refreshToken));
        registro.setUsuarioId(usuario.getId());
        registro.setExpiraEn(ahora.plus(Duration.ofDays(properties.seguridad().refreshExpiracionDias())));
        registro.setRevocado(false);
        registro.setCreadoEn(ahora);
        refreshTokens.save(registro);

        return new AuthDtos.AuthResponse(TIPO_TOKEN, accessToken, refreshToken,
            jwtService.getExpiracionSegundos(), resumen(usuario));
    }

    private String generarRefreshToken() {
        byte[] bytes = new byte[48];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthDtos.UsuarioResumen resumen(Usuario usuario) {
        return new AuthDtos.UsuarioResumen(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
            usuario.getCorreo(), usuario.getRol());
    }

    private String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
