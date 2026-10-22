package com.alexrifas.api.controller;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.SorteoService;
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
@RequestMapping("/api")
public class SorteoController {
    private final SorteoService sorteos;

    public SorteoController(SorteoService sorteos) { this.sorteos = sorteos; }

    @PostMapping("/organizador/rifas/{rifaId}/sortear")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SorteoDtos.GanadorDto> sortear(@PathVariable Long rifaId,
            @Valid @RequestBody(required = false) SorteoDtos.SorteoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return sorteos.sortear(rifaId, request, actor);
    }

    @GetMapping("/rifas/{codigo}/ganadores")
    public List<SorteoDtos.GanadorDto> ganadores(@PathVariable String codigo) {
        return sorteos.consultarGanadores(codigo);
    }
}
