package com.alexrifas.api.controller;

import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.LoteriaService;
import com.alexrifas.api.service.ResultadoLoteriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de loterías y resultados cargados a mano (CP-08). Todo bajo {@code /api/admin/**} (solo ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminLoteriaController {
    private final LoteriaService loterias;
    private final ResultadoLoteriaService resultados;

    public AdminLoteriaController(LoteriaService loterias, ResultadoLoteriaService resultados) {
        this.loterias = loterias;
        this.resultados = resultados;
    }

    @GetMapping("/loterias")
    public List<LoteriaDtos.LoteriaDto> listar(@RequestParam(required = false) Boolean activa,
                                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        return loterias.listar(activa, actor);
    }

    @PostMapping("/loterias")
    @ResponseStatus(HttpStatus.CREATED)
    public LoteriaDtos.LoteriaDto crear(@Valid @RequestBody LoteriaDtos.LoteriaRequest request,
                                        @AuthenticationPrincipal UsuarioAutenticado actor) {
        return loterias.crear(request, actor);
    }

    @PutMapping("/loterias/{id}")
    public LoteriaDtos.LoteriaDto actualizar(@PathVariable Long id, @Valid @RequestBody LoteriaDtos.LoteriaRequest request,
                                             @AuthenticationPrincipal UsuarioAutenticado actor) {
        return loterias.actualizar(id, request, actor);
    }

    /** {@code ?estado=PENDIENTE_VERIFICACION&pagina=0&tamano=20} (todo opcional). */
    @GetMapping("/loterias/{id}/resultados")
    public RifaDtos.Pagina<LoteriaDtos.ResultadoDto> listarResultados(@PathVariable Long id,
            @RequestParam(required = false) EstadoResultado estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return resultados.listar(id, estado, pagina, tamano, actor);
    }

    @PostMapping("/loterias/{id}/resultados")
    @ResponseStatus(HttpStatus.CREATED)
    public LoteriaDtos.ResultadoDto cargarResultado(@PathVariable Long id,
            @Valid @RequestBody LoteriaDtos.CargarResultadoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return resultados.cargar(id, request, actor);
    }

    @PostMapping("/resultados-loteria/{id}/verificar")
    public LoteriaDtos.ResultadoDto verificar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return resultados.verificar(id, actor);
    }

    @PostMapping("/resultados-loteria/{id}/anular")
    public LoteriaDtos.ResultadoDto anular(@PathVariable Long id,
            @Valid @RequestBody LoteriaDtos.AnularResultadoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return resultados.anular(id, request, actor);
    }
}
