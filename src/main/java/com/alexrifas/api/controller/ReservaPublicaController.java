package com.alexrifas.api.controller;

import com.alexrifas.api.config.AntiabusoProperties;
import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.service.CompraService;
import com.alexrifas.api.service.IpCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints sin autenticación para compradores invitados (ADR-04). */
@RestController
@RequestMapping("/api/publico/reservas")
public class ReservaPublicaController {
    private final CompraService compras;
    private final AntiabusoProperties antiabuso;

    public ReservaPublicaController(CompraService compras, AntiabusoProperties antiabuso) {
        this.compras = compras;
        this.antiabuso = antiabuso;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraDtos.ReservaPublicaResponse reservar(@Valid @RequestBody CompraDtos.ReservaPublicaRequest request,
            HttpServletRequest http) {
        String ip = IpCliente.resolver(http.getHeader("X-Forwarded-For"), http.getRemoteAddr(), antiabuso.saltosProxy());
        return compras.reservarInvitado(request, ip);
    }

    @GetMapping("/{codigo}")
    public CompraDtos.ReservaPublicaResponse consultar(@PathVariable String codigo,
            @RequestParam String telefono) {
        return compras.consultarInvitado(codigo, telefono);
    }
}
