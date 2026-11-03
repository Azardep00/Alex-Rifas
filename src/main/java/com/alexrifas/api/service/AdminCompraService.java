package com.alexrifas.api.service;

import com.alexrifas.api.dto.AdminDtos;
import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.CompraRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administración de compras y resumen de rifa (CP-06). Solo para administradores. */
@Service
public class AdminCompraService {
    private static final Logger log = LoggerFactory.getLogger(AdminCompraService.class);
    static final int TAMANO_MAXIMO_PAGINA = 100;

    private final CompraRepository compras;
    private final BoletoRepository boletos;
    private final RifaRepository rifas;
    private final AntiabusoService antiabuso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AdminCompraService(CompraRepository compras, BoletoRepository boletos, RifaRepository rifas,
                              AntiabusoService antiabuso, AuditoriaService auditoria, Clock clock) {
        this.compras = compras;
        this.boletos = boletos;
        this.rifas = rifas;
        this.antiabuso = antiabuso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Lista compras con filtros opcionales. Las pendientes salen primero las que vencen antes (la cola de pagos
     * se atiende de la más urgente); el resto, de la más reciente a la más antigua.
     */
    @Transactional(readOnly = true)
    public RifaDtos.Pagina<AdminDtos.CompraAdminDto> listar(UsuarioAutenticado actor, EstadoCompra estado,
            String codigoRifa, String telefono, int pagina, int tamano) {
        return listar(actor, estado, codigoRifa, telefono, null, pagina, tamano);
    }

    /** Igual que arriba, y con {@code numero} solo salen las compras que tienen ese número ahora mismo (CP-18). */
    @Transactional(readOnly = true)
    public RifaDtos.Pagina<AdminDtos.CompraAdminDto> listar(UsuarioAutenticado actor, EstadoCompra estado,
            String codigoRifa, String telefono, Integer numero, int pagina, int tamano) {
        exigirAdmin(actor);
        Long rifaId = null;
        if (codigoRifa != null && !codigoRifa.isBlank()) {
            rifaId = rifas.findByCodigoPublico(codigoRifa.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."))
                .getId();
        }
        String telefonoNormalizado = telefono == null || telefono.isBlank() ? null : TelefonoColombia.normalizar(telefono);

        Sort orden = estado == EstadoCompra.PENDIENTE
            ? Sort.by(Sort.Direction.ASC, "expiraEn")
            : Sort.by(Sort.Direction.DESC, "creadaEn");
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);

        final List<Long> idsPorNumero = numero == null ? null : boletos.findCompraIdsByNumero(numero);
        if (idsPorNumero != null && idsPorNumero.isEmpty()) {
            return new RifaDtos.Pagina<>(List.of(), p, t, 0, 0);
        }
        final Long rifaFiltro = rifaId;
        Specification<Compra> filtro = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (rifaFiltro != null) {
                condiciones.add(cb.equal(root.get("rifaId"), rifaFiltro));
            }
            if (telefonoNormalizado != null) {
                condiciones.add(cb.equal(root.get("compradorTelefono"), telefonoNormalizado));
            }
            if (idsPorNumero != null) {
                condiciones.add(root.get("id").in(idsPorNumero));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        Page<Compra> resultado = compras.findAll(filtro, PageRequest.of(p, t, orden));
        return new RifaDtos.Pagina<>(aDtos(resultado.getContent()), resultado.getNumber(), resultado.getSize(),
            resultado.getTotalElements(), resultado.getTotalPages());
    }

    /**
     * Libera una reserva pendiente: la compra pasa a CANCELADA y sus boletos vuelven a estar disponibles.
     * No aplica a compras confirmadas (devolver un pago es otro proceso) ni a las que ya vencieron o se cancelaron.
     */
    @Transactional
    public AdminDtos.LiberacionResponse liberar(String codigo, AdminDtos.LiberacionRequest request,
                                                UsuarioAutenticado actor) {
        exigirAdmin(actor);
        Compra compra = compras.findByCodigoForUpdate(normalizarCodigo(codigo))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la compra."));
        if (compra.getEstado() != EstadoCompra.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden liberar reservas pendientes de pago; esta compra ya está "
                    + compra.getEstado().name().toLowerCase(Locale.ROOT) + ".");
        }
        Instant ahora = Instant.now(clock);

        int liberados = 0;
        List<Boleto> relacionados = boletos.findByCompraId(compra.getId());
        if (!relacionados.isEmpty()) {
            List<Integer> numeros = relacionados.stream().map(Boleto::getNumero).toList();
            List<Boleto> bloqueados = boletos.findByRifaIdAndNumeroInForUpdate(compra.getRifaId(), numeros);
            for (Boleto boleto : bloqueados) {
                // Si la reserva ya había vencido, otro comprador pudo tomar el número: no se le quita.
                if (boleto.getEstado() == EstadoBoleto.RESERVADO && compra.getId().equals(boleto.getCompraId())) {
                    boleto.setEstado(EstadoBoleto.DISPONIBLE);
                    boleto.setCompraId(null);
                    boleto.setReservadoHasta(null);
                    liberados++;
                }
            }
            boletos.saveAll(bloqueados);
        }
        compra.setEstado(EstadoCompra.CANCELADA);
        compra.setActualizadaEn(ahora);
        compras.save(compra);
        antiabuso.liberar(compra.getCodigo()); // devuelve el cupo por IP (CP-05)
        Map<String, Object> rastro = new LinkedHashMap<>();
        rastro.put("rifa", compra.getRifaId());
        rastro.put("boletosLiberados", liberados);
        rastro.put("motivo", motivoParaLog(request));
        auditoria.registrar(actor, "compra", compra.getCodigo(), "RESERVA_LIBERADA", rastro);
        log.info("Compra {} liberada por el administrador {} ({} boletos). Motivo: {}", compra.getCodigo(),
            actor.id(), liberados, motivoParaLog(request));
        return new AdminDtos.LiberacionResponse(compra.getCodigo(), compra.getEstado(), liberados, ahora);
    }

    @Transactional(readOnly = true)
    public AdminDtos.ResumenRifa resumenRifa(String codigoRifa, UsuarioAutenticado actor) {
        exigirAdmin(actor);
        Rifa rifa = rifas.findByCodigoPublico(normalizarCodigo(codigoRifa))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        Instant ahora = Instant.now(clock);
        Long id = rifa.getId();
        int cantidad = rifa.getCantidadBoletos();

        long vendidos = boletos.countByRifaIdAndEstado(id, EstadoBoleto.VENDIDO);
        long reservados = boletos.countByRifaIdAndEstadoAndReservadoHastaAfter(id, EstadoBoleto.RESERVADO, ahora);
        long disponibles = Math.max(0, cantidad - vendidos - reservados);
        BigDecimal porcentaje = cantidad <= 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(vendidos).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);

        Map<EstadoCompra, Long> porEstado = new HashMap<>();
        for (CompraRepository.EstadoConteo fila : compras.contarPorEstado(id)) {
            porEstado.put(fila.getEstado(), fila.getTotal());
        }
        return new AdminDtos.ResumenRifa(rifa.getCodigoPublico(), rifa.getTitulo(), rifa.getEstado(), rifa.getMoneda(),
            rifa.getPrecioBoleto(), cantidad, disponibles, reservados, vendidos, porcentaje,
            cero(compras.sumarSubtotal(id, EstadoCompra.CONFIRMADA)),
            cero(compras.sumarPendientesVigentes(id, ahora)),
            rifa.getPrecioBoleto().multiply(BigDecimal.valueOf(cantidad)),
            porEstado.getOrDefault(EstadoCompra.PENDIENTE, 0L), porEstado.getOrDefault(EstadoCompra.CONFIRMADA, 0L),
            porEstado.getOrDefault(EstadoCompra.EXPIRADA, 0L), porEstado.getOrDefault(EstadoCompra.CANCELADA, 0L),
            rifa.getCierreVentasEn(), rifa.getFechaSorteo());
    }

    /** Arma las filas con 2 consultas en lote (boletos y rifas) en vez de 2 por compra. */
    private List<AdminDtos.CompraAdminDto> aDtos(List<Compra> pagina) {
        if (pagina.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Integer>> numerosPorCompra = boletos
            .findByCompraIdIn(pagina.stream().map(Compra::getId).toList()).stream()
            .collect(Collectors.groupingBy(Boleto::getCompraId,
                Collectors.mapping(Boleto::getNumero, Collectors.collectingAndThen(Collectors.toList(),
                    lista -> lista.stream().sorted(Comparator.naturalOrder()).toList()))));
        Map<Long, Rifa> rifasPorId = rifas.findAllById(pagina.stream().map(Compra::getRifaId).distinct().toList())
            .stream().collect(Collectors.toMap(Rifa::getId, r -> r));
        return pagina.stream().map(c -> {
            Rifa rifa = rifasPorId.get(c.getRifaId());
            return new AdminDtos.CompraAdminDto(c.getCodigo(), rifa == null ? null : rifa.getCodigoPublico(),
                c.getEstado(), c.getCompradorNombre(), c.getCompradorTelefono(), c.getUsuarioId(),
                numerosPorCompra.getOrDefault(c.getId(), List.of()), c.getSubtotal(),
                rifa == null ? null : rifa.getMoneda(), c.getExpiraEn(), c.getCreadaEn(), c.getActualizadaEn());
        }).toList();
    }

    private void exigirAdmin(UsuarioAutenticado actor) {
        if (actor == null || actor.rol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede hacer esta operación.");
        }
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** El motivo lo escribe una persona: se aplana a una línea y se acota para que no falsee el log. */
    private static String motivoParaLog(AdminDtos.LiberacionRequest request) {
        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            return "(sin motivo)";
        }
        String plano = request.motivo().replaceAll("[\\r\\n\\t]+", " ").trim();
        return plano.length() > 200 ? plano.substring(0, 200) + "…" : plano;
    }
}
