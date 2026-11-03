package com.alexrifas.api.controller;

import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.LoteriaService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CP-17: catálogo de loterías activas, de solo lectura, para el wizard de rifas (ORGANIZADOR o ADMIN). */
@RestController
@RequestMapping("/api/organizador/loterias")
public class OrganizadorLoteriaController {
    private final LoteriaService loterias;

    public OrganizadorLoteriaController(LoteriaService loterias) { this.loterias = loterias; }

    @GetMapping
    public List<LoteriaDtos.LoteriaDto> activas(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return loterias.listarActivasParaGestor(actor);
    }
}
