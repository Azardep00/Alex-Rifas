package com.alexrifas.api.controller;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.service.GrillaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rifas")
public class GrillaController {
    private final GrillaService grilla;

    public GrillaController(GrillaService grilla) { this.grilla = grilla; }

    /** Público (ya cubierto por GET /api/rifas/** en SecurityConfig). */
    @GetMapping("/{codigo}/boletos")
    public RifaDtos.GrillaBoletos boletos(@PathVariable String codigo) {
        return grilla.grilla(codigo);
    }
}
