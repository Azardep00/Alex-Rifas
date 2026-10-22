package com.alexrifas.api.service;

import com.alexrifas.api.dto.CompraDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.entity.PagoWompi;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.PagoWompiRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompraService {
    private static final Logger log = LoggerFactory.getLogger(CompraService.class);
    private static final Duration RESERVA_DURACION = Duration.ofMinutes(15);
    private static final int MAX_NUMEROS_POR_SOLICITUD = 100;

    private final CompraRepository compras;
    private final BoletoRepository boletos;
    private final RifaRepository rifas;
    private final PagoWompiRepository pagos;
    private final Clock clock;

    public CompraService(CompraRepository compras, BoletoRepository boletos, RifaRepository rifas,
                         PagoWompiRepository pagos, Clock clock) {
        this.compras = compras;
        this.boletos = boletos;
        this.rifas = rifas;
        this.pagos = pagos;
        this.clock = clock;
    }

    @Transactional
    public CompraDtos.CompraResponse reservar(CompraDtos.ReservaRequest request, UsuarioAutenticado actor) {
        exigirCliente(actor);
        List<Integer> numeros = validarNumeros(request.numeros());
        Instant ahora = Instant.now(clock);
        Rifa rifa = rifas.findByCodigoPublico(request.codigoRifa().trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        if (rifa.getEstado() != EstadoRifa.PUBLICADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La rifa no está disponible para reservar boletos.");
        }
        if (!rifa.getCierreVentasEn().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El periodo de venta de esta rifa terminó.");
        }
        if (numeros.size() > rifa.getMaximoBoletosPorCompra()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Puedes reservar máximo " + rifa.getMaximoBoletosPorCompra() + " boletos por compra.");
        }

        List<Boleto> seleccionados = boletos.findByRifaIdAndNumeroInForUpdate(rifa.getId(), numeros);
        if (seleccionados.size() != numeros.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más números no pertenecen a esta rifa.");
        }
        for (Boleto boleto : seleccionados) {
            if (boleto.getEstado() == EstadoBoleto.RESERVADO
                    && boleto.getReservadoHasta() != null && !boleto.getReservadoHasta().isAfter(ahora)) {
                // El plazo ya venció. Se libera en esta misma transacción antes de validar disponibilidad.
                boleto.setEstado(EstadoBoleto.DISPONIBLE);
                boleto.setCompraId(null);
                boleto.setReservadoHasta(null);
            }
            if (boleto.getEstado() != EstadoBoleto.DISPONIBLE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El boleto " + boleto.getNumero() + " ya no está disponible.");
            }
        }

        Instant vence = ahora.plus(RESERVA_DURACION);
        if (rifa.getCierreVentasEn().isBefore(vence)) {
            vence = rifa.getCierreVentasEn();
        }
        Compra compra = new Compra();
        compra.setCodigo(UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        compra.setRifaId(rifa.getId());
        compra.setUsuarioId(actor.id());
        compra.setEstado(EstadoCompra.PENDIENTE);
        compra.setSubtotal(rifa.getPrecioBoleto().multiply(BigDecimal.valueOf(seleccionados.size())));
        compra.setExpiraEn(vence);
        compra.setCreadaEn(ahora);
        compra.setActualizadaEn(ahora);
        compras.saveAndFlush(compra);

        for (Boleto boleto : seleccionados) {
            boleto.setEstado(EstadoBoleto.RESERVADO);
            boleto.setCompraId(compra.getId());
            boleto.setReservadoHasta(vence);
        }
        boletos.saveAll(seleccionados);
        return respuesta(compra, rifa, seleccionados);
    }

    @Transactional(readOnly = true)
    public List<CompraDtos.CompraResponse> misCompras(UsuarioAutenticado actor) {
        return compras.findByUsuarioIdOrderByCreadaEnDesc(actor.id()).stream()
            .map(compra -> respuestaConConsultas(compra)).toList();
    }

    @Transactional(readOnly = true)
    public CompraDtos.CompraResponse consultar(String codigo, UsuarioAutenticado actor) {
        Compra compra = compras.findByCodigoAndUsuarioId(codigo, actor.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la compra."));
        return respuestaConConsultas(compra);
    }

    @Transactional
    public CompraDtos.ConfirmacionResponse confirmarPagoManual(String codigo,
            CompraDtos.ConfirmacionManualRequest request, UsuarioAutenticado actor) {
        if (actor.rol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede confirmar pagos manualmente.");
        }
        Compra compra = compras.findByCodigoForUpdate(codigo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la compra."));
        Instant ahora = Instant.now(clock);
        if (compra.getEstado() != EstadoCompra.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La compra ya no está pendiente de pago.");
        }
        if (!compra.getExpiraEn().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva venció; no se puede confirmar este pago.");
        }
        Rifa rifa = rifas.findById(compra.getRifaId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        List<Boleto> relacionados = boletos.findByCompraId(compra.getId());
        if (relacionados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La compra no tiene boletos asociados.");
        }
        List<Integer> numeros = relacionados.stream().map(Boleto::getNumero).toList();
        List<Boleto> bloqueados = boletos.findByRifaIdAndNumeroInForUpdate(rifa.getId(), numeros);
        if (bloqueados.size() != relacionados.size() || bloqueados.stream().anyMatch(b ->
                b.getEstado() != EstadoBoleto.RESERVADO || !compra.getId().equals(b.getCompraId())
                || b.getReservadoHasta() == null || !b.getReservadoHasta().isAfter(ahora))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ya no es válida; no se confirmó el pago.");
        }
        if (pagos.existsByCompraIdAndEstado(compra.getId(), "CONFIRMADO_MANUAL")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta compra ya tiene una confirmación manual registrada.");
        }

        String referencia = "MANUAL-" + compra.getCodigo();
        PagoWompi pago = new PagoWompi();
        pago.setCompraId(compra.getId());
        pago.setReferencia(referencia);
        pago.setTransaccionId(referencia);
        pago.setEstado("CONFIRMADO_MANUAL");
        pago.setMonto(compra.getSubtotal());
        pago.setMoneda(rifa.getMoneda());
        Map<String, Object> evidencia = new LinkedHashMap<>();
        evidencia.put("tipo", "CONFIRMACION_MANUAL");
        evidencia.put("metodo", request.metodo());
        evidencia.put("observacion", request.observacion());
        evidencia.put("validadoPorUsuarioId", actor.id());
        pago.setRespuestaJson(evidencia);
        pago.setCreadoEn(ahora);
        pago.setActualizadoEn(ahora);
        pagos.save(pago);

        for (Boleto boleto : bloqueados) {
            boleto.setEstado(EstadoBoleto.VENDIDO);
            boleto.setReservadoHasta(null);
        }
        boletos.saveAll(bloqueados);
        compra.setEstado(EstadoCompra.CONFIRMADA);
        compra.setActualizadaEn(ahora);
        compras.save(compra);
        return new CompraDtos.ConfirmacionResponse(compra.getCodigo(), compra.getEstado(), referencia, ahora);
    }

    /** Expira reservas vencidas y devuelve sus boletos al inventario. */
    @Scheduled(fixedDelayString = "${app.compras.expirar-cada-ms:30000}")
    @Transactional
    public void expirarReservasVencidas() {
        Instant ahora = Instant.now(clock);
        List<Compra> vencidas = compras.findByEstadoAndExpiraEnLessThanEqual(EstadoCompra.PENDIENTE, ahora);
        for (Compra compra : vencidas) {
            List<Boleto> relacionados = boletos.findByCompraId(compra.getId());
            for (Boleto boleto : relacionados) {
                if (boleto.getEstado() == EstadoBoleto.RESERVADO && compra.getId().equals(boleto.getCompraId())) {
                    boleto.setEstado(EstadoBoleto.DISPONIBLE);
                    boleto.setCompraId(null);
                    boleto.setReservadoHasta(null);
                }
            }
            boletos.saveAll(relacionados);
            compra.setEstado(EstadoCompra.EXPIRADA);
            compra.setActualizadaEn(ahora);
            compras.save(compra);
        }
        if (!vencidas.isEmpty()) {
            log.info("Se expiraron {} reservas vencidas", vencidas.size());
        }
    }

    private List<Integer> validarNumeros(List<Integer> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un número.");
        }
        if (valores.size() > MAX_NUMEROS_POR_SOLICITUD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud supera el límite de números.");
        }
        Set<Integer> unicos = new LinkedHashSet<>(valores);
        if (unicos.size() != valores.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No repitas números en la misma reserva.");
        }
        return new ArrayList<>(unicos);
    }

    private CompraDtos.CompraResponse respuestaConConsultas(Compra compra) {
        Rifa rifa = rifas.findById(compra.getRifaId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa asociada."));
        return respuesta(compra, rifa, boletos.findByCompraId(compra.getId()));
    }

    private CompraDtos.CompraResponse respuesta(Compra compra, Rifa rifa, List<Boleto> boletosCompra) {
        List<Integer> numeros = boletosCompra.stream().map(Boleto::getNumero).sorted().toList();
        return new CompraDtos.CompraResponse(compra.getCodigo(), rifa.getCodigoPublico(), compra.getEstado(),
            numeros, compra.getSubtotal(), rifa.getMoneda(), compra.getExpiraEn(), compra.getCreadaEn(), compra.getActualizadaEn());
    }

    private void exigirCliente(UsuarioAutenticado actor) {
        if (actor.rol() != Rol.CLIENTE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los clientes pueden reservar boletos.");
        }
    }
}
