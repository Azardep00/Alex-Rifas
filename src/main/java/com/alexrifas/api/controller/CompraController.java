package com.alexrifas.api.controller;

import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.CompraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compras")
public class CompraController {
    private final CompraService compras;

    public CompraController(CompraService compras) { this.compras = compras; }

    @PostMapping("/reservar")
    @ResponseStatus(HttpStatus.CREATED)
    public CompraDtos.CompraResponse reservar(@Valid @RequestBody CompraDtos.ReservaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.reservar(request, actor);
    }

    @GetMapping("/mias")
    public List<CompraDtos.CompraResponse> misCompras(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.misCompras(actor);
    }

    @GetMapping("/{codigo}")
    public CompraDtos.CompraResponse consultar(@PathVariable String codigo,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.consultar(codigo, actor);
    }
}
