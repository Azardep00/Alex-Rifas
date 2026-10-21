package com.alexrifas.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.alexrifas.api.config.AppProperties;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida los access tokens (JWT firmados con HMAC-SHA256). Duran pocos minutos: la sesión larga
 * se mantiene con el refresh token, que sí se puede revocar. El token solo lleva el id y el rol.
 */
@Service
public class JwtService {

    private static final String EMISOR = "alex-rifas";

    private final SecretKey clave;
    private final long expiracionSegundos;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        // AppProperties ya exige un secreto de mínimo 32 caracteres (256 bits).
        this.clave = Keys.hmacShaKeyFor(props.seguridad().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expiracionSegundos = props.seguridad().jwtExpiracionMinutos() * 60;
        this.clock = clock;
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }

    public String generarAccessToken(Usuario usuario) {
        Instant ahora = Instant.now(clock);
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuario.getId()))
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException si el token está vencido, mal firmado o mal formado
     * @throws IllegalArgumentException si el contenido del token no es el esperado
     */
    public UsuarioAutenticado validar(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(clave)
                .requireIssuer(EMISOR)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long id = Long.valueOf(claims.getSubject());
        Rol rol = Rol.valueOf(claims.get("rol", String.class));
        return new UsuarioAutenticado(id, rol);
    }
}
