package com.alexrifas.api.controller;

import com.alexrifas.api.dto.AuthDtos;
import com.alexrifas.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    /** Only ADMIN can create organizer accounts; public registration never accepts a role. */
    @PostMapping("/organizadores")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UsuarioDto crearOrganizador(@Valid @RequestBody AuthDtos.CrearOrganizadorRequest request) {
        return authService.crearOrganizador(request);
    }
}
