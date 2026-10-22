package com.alexrifas.api.controller;

import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/compras")
public class AdminCompraController {
    private final CompraService compras;

    public AdminCompraController(CompraService compras) { this.compras = compras; }

    @PostMapping("/{codigo}/confirmar-pago-manual")
    public CompraDtos.ConfirmacionResponse confirmarPagoManual(@PathVariable String codigo,
            @Valid @RequestBody CompraDtos.ConfirmacionManualRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.confirmarPagoManual(codigo, request, actor);
    }
}
