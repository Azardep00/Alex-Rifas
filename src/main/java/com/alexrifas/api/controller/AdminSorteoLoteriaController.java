package com.alexrifas.api.controller;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.SorteoLoteriaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sorteo con el resultado de una lotería (CP-09). Bajo {@code /api/admin/**}: solo ADMIN. */
@RestController
@RequestMapping("/api/admin/rifas/{rifaId}/sortear-por-loteria")
public class AdminSorteoLoteriaController {
    private final SorteoLoteriaService sorteos;

    public AdminSorteoLoteriaController(SorteoLoteriaService sorteos) { this.sorteos = sorteos; }

    /** Muestra qué ganaría cada premio con los resultados actuales, sin guardar nada. */
    @GetMapping("/previsualizar")
    public SorteoDtos.SorteoLoteriaDto previsualizar(@PathVariable Long rifaId,
                                                     @AuthenticationPrincipal UsuarioAutenticado actor) {
        return sorteos.previsualizar(rifaId, actor);
    }

    /** Ejecuta el sorteo: decide todos los premios y deja la rifa SORTEADA, o responde 409 sin guardar nada. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SorteoDtos.SorteoLoteriaDto ejecutar(@PathVariable Long rifaId,
                                                @AuthenticationPrincipal UsuarioAutenticado actor) {
        return sorteos.ejecutar(rifaId, actor);
    }
}
