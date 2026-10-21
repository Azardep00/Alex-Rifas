package com.alexrifas.api.controller;

import com.alexrifas.api.dto.AuthDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<AuthDtos.AuthResponse> registrar(@Valid @RequestBody AuthDtos.RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse renovar(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.renovar(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authService.cerrarSesion(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.UsuarioDto actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return authService.usuarioActual(usuario.id());
    }
}
