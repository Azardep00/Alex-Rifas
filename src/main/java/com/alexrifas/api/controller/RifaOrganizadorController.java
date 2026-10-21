package com.alexrifas.api.controller;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.RifaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizador/rifas")
public class RifaOrganizadorController {
    private final RifaService rifaService;

    public RifaOrganizadorController(RifaService rifaService) {
        this.rifaService = rifaService;
    }

    @GetMapping
    public RifaDtos.PaginaDto<RifaDtos.RifaResumenDto> listar(
            @AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {
        return rifaService.listarPropias(actor, pagina, tamano);
    }

    @PostMapping
    public ResponseEntity<RifaDtos.RifaDetalleDto> crear(@Valid @RequestBody RifaDtos.RifaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rifaService.crear(request, actor));
    }

    @PutMapping("/{id}")
    public RifaDtos.RifaDetalleDto actualizar(@PathVariable Long id, @Valid @RequestBody RifaDtos.RifaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifaService.actualizar(id, request, actor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        rifaService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publicar")
    public RifaDtos.RifaDetalleDto publicar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifaService.publicar(id, actor);
    }

    @PostMapping("/{id}/cerrar")
    public RifaDtos.RifaDetalleDto cerrar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifaService.cerrar(id, actor);
    }

    @PostMapping("/{id}/premios")
    public ResponseEntity<RifaDtos.PremioDto> crearPremio(@PathVariable Long id,
            @Valid @RequestBody RifaDtos.PremioRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rifaService.crearPremio(id, request, actor));
    }

    @PutMapping("/{id}/premios/{premioId}")
    public RifaDtos.PremioDto actualizarPremio(@PathVariable Long id, @PathVariable Long premioId,
            @Valid @RequestBody RifaDtos.PremioRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return rifaService.actualizarPremio(id, premioId, request, actor);
    }

    @DeleteMapping("/{id}/premios/{premioId}")
    public ResponseEntity<Void> eliminarPremio(@PathVariable Long id, @PathVariable Long premioId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        rifaService.eliminarPremio(id, premioId, actor);
        return ResponseEntity.noContent().build();
    }
}
