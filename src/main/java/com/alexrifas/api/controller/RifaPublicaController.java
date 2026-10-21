package com.alexrifas.api.controller;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.service.RifaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rifas")
public class RifaPublicaController {
    private final RifaService rifaService;

    public RifaPublicaController(RifaService rifaService) {
        this.rifaService = rifaService;
    }

    @GetMapping
    public RifaDtos.PaginaDto<RifaDtos.RifaResumenDto> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {
        return rifaService.listarPublicas(pagina, tamano);
    }

    @GetMapping("/{codigo}")
    public RifaDtos.RifaDetalleDto detalle(@PathVariable String codigo) {
        return rifaService.obtenerPublica(codigo);
    }
}
