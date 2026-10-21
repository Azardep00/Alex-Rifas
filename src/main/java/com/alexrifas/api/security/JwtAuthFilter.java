package com.alexrifas.api.security;

import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads Bearer access tokens and puts the authenticated user/role into Spring Security's context. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    UsuarioAutenticado usuario = jwtService.validar(token);
                    Usuario usuarioActual = usuarioRepository.findById(usuario.id())
                        .filter(Usuario::isActivo)
                        .filter(db -> db.getRol() == usuario.rol())
                        .orElse(null);
                    if (usuarioActual != null) {
                        Rol rol = usuario.rol();
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
                        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (JwtException | IllegalArgumentException ex) {
                    // Do not reveal whether a token expired, was forged or had invalid claims.
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
