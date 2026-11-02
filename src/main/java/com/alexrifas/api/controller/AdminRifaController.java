package com.alexrifas.api.controller;

import com.alexrifas.api.dto.AdminDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.AdminCompraService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rifas")
public class AdminRifaController {
    private final AdminCompraService admin;

    public AdminRifaController(AdminCompraService admin) { this.admin = admin; }

    @GetMapping("/{codigo}/resumen")
    public AdminDtos.ResumenRifa resumen(@PathVariable String codigo, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return admin.resumenRifa(codigo, actor);
    }
}
