package com.alexrifas.api.controller;

import com.alexrifas.api.dto.AdminDtos;
import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.security.UsuarioAutenticado;
import com.alexrifas.api.service.AdminCompraService;
import com.alexrifas.api.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/compras")
public class AdminCompraController {
    private final CompraService compras;
    private final AdminCompraService admin;

    public AdminCompraController(CompraService compras, AdminCompraService admin) {
        this.compras = compras;
        this.admin = admin;
    }

    /** Cola de pagos: {@code ?estado=PENDIENTE&rifa=ABC123&telefono=300 123 4567&pagina=0&tamano=20} (todo opcional). */
    @GetMapping
    public RifaDtos.Pagina<AdminDtos.CompraAdminDto> listar(
            @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(required = false) String rifa,
            @RequestParam(required = false) String telefono,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return admin.listar(actor, estado, rifa, telefono, pagina, tamano);
    }

    @PostMapping("/{codigo}/confirmar-pago-manual")
    public CompraDtos.ConfirmacionResponse confirmarPagoManual(@PathVariable String codigo,
            @Valid @RequestBody CompraDtos.ConfirmacionManualRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.confirmarPagoManual(codigo, request, actor);
    }

    /** Libera una reserva pendiente (cuerpo opcional: {@code {"motivo": "..."}}). */
    @PostMapping("/{codigo}/liberar")
    public AdminDtos.LiberacionResponse liberar(@PathVariable String codigo,
            @Valid @RequestBody(required = false) AdminDtos.LiberacionRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return admin.liberar(codigo, request, actor);
    }
}
