package com.alexrifas.api.controller;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.RifaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RifaController {
    private final RifaService rifas;

    public RifaController(RifaService rifas) { this.rifas = rifas; }

    // ---- público (lo consume el sitio web Flask)

    @GetMapping("/rifas")
    public RifaDtos.Pagina<RifaDtos.RifaResumen> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {
        return rifas.listarPublicas(pagina, tamano);
    }

    @GetMapping("/rifas/{codigo}")
    public RifaDtos.RifaDetalle detalle(@PathVariable String codigo) {
        return rifas.detallePublico(codigo);
    }

    // ---- organizador / administrador

    @PostMapping("/organizador/rifas")
    @ResponseStatus(HttpStatus.CREATED)
    public RifaDtos.RifaGestionDto crear(@Valid @RequestBody RifaDtos.RifaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.crear(request, actor);
    }

    @GetMapping("/organizador/rifas")
    public RifaDtos.Pagina<RifaDtos.RifaGestionDto> misRifas(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.misRifas(actor, pagina, tamano);
    }

    @PostMapping("/organizador/rifas/{rifaId}/premios")
    @ResponseStatus(HttpStatus.CREATED)
    public RifaDtos.PremioDto agregarPremio(@PathVariable Long rifaId,
            @Valid @RequestBody RifaDtos.PremioRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.agregarPremio(rifaId, request, actor);
    }

    @PostMapping("/organizador/rifas/{rifaId}/publicar")
    public RifaDtos.RifaGestionDto publicar(@PathVariable Long rifaId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.publicar(rifaId, actor);
    }

    @PostMapping("/organizador/rifas/{rifaId}/cerrar")
    public RifaDtos.RifaGestionDto cerrar(@PathVariable Long rifaId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.cerrar(rifaId, actor);
    }

    @PostMapping("/organizador/rifas/{rifaId}/cancelar")
    public RifaDtos.RifaGestionDto cancelar(@PathVariable Long rifaId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifas.cancelar(rifaId, actor);
    }
}
