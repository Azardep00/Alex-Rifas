package com.alexrifas.api.service;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.repository.UsuarioRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RifaService {
    private static final int MAX_BOLETOS = 50_000;
    private static final int TAMANO_LOTE = 1_000;

    private final RifaRepository rifas;
    private final PremioRepository premios;
    private final BoletoRepository boletos;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public RifaService(RifaRepository rifas, PremioRepository premios, BoletoRepository boletos,
                       UsuarioRepository usuarios, Clock clock) {
        this.rifas = rifas;
        this.premios = premios;
        this.boletos = boletos;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RifaDtos.PaginaDto<RifaDtos.RifaResumenDto> listarPublicas(int pagina, int tamano) {
        Page<Rifa> resultado = rifas.findByEstadoOrderByFechaSorteoAsc(EstadoRifa.PUBLICADA,
            PageRequest.of(paginaSegura(pagina), tamanoSeguro(tamano)));
        return pagina(resultado, this::aResumen);
    }

    @Transactional(readOnly = true)
    public RifaDtos.RifaDetalleDto obtenerPublica(String codigo) {
        Rifa rifa = rifas.findByCodigoPublico(codigo.toUpperCase(Locale.ROOT))
            .filter(r -> r.getEstado() != EstadoRifa.BORRADOR && r.getEstado() != EstadoRifa.CANCELADA)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa publicada."));
        return aDetalle(rifa);
    }

    @Transactional(readOnly = true)
    public RifaDtos.PaginaDto<RifaDtos.RifaResumenDto> listarPropias(UsuarioAutenticado actor, int pagina, int tamano) {
        Page<Rifa> resultado;
        if (actor.rol() == Rol.ADMIN) {
            resultado = rifas.findAll(PageRequest.of(paginaSegura(pagina), tamanoSeguro(tamano), Sort.by("creadaEn").descending()));
        } else {
            resultado = rifas.findByOrganizadorIdOrderByCreadaEnDesc(actor.id(),
                PageRequest.of(paginaSegura(pagina), tamanoSeguro(tamano)));
        }
        return pagina(resultado, this::aResumen);
    }

    @Transactional
    public RifaDtos.RifaDetalleDto crear(RifaDtos.RifaRequest request, UsuarioAutenticado actor) {
        validarPuedeOrganizar(actor);
        validarFechas(request.cierreVentasEn(), request.fechaSorteo());
        validarCantidad(request.cantidadBoletos(), request.maximoBoletosPorCompra());
        Rifa rifa = new Rifa();
        rifa.setCodigoPublico(nuevoCodigo());
        rifa.setOrganizadorId(actor.id());
        copiarDatos(rifa, request);
        rifa.setEstado(EstadoRifa.BORRADOR);
        Instant ahora = Instant.now(clock);
        rifa.setCreadaEn(ahora);
        rifa.setActualizadaEn(ahora);
        rifa.setPublicadaEn(null);
        rifa = rifas.save(rifa);
        generarBoletos(rifa);
        return aDetalle(rifa);
    }

    @Transactional
    public RifaDtos.RifaDetalleDto actualizar(Long id, RifaDtos.RifaRequest request, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(id, actor);
        exigirBorrador(rifa);
        validarFechas(request.cierreVentasEn(), request.fechaSorteo());
        validarCantidad(request.cantidadBoletos(), request.maximoBoletosPorCompra());
        if (request.cantidadBoletos() != rifa.getCantidadBoletos()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La cantidad de boletos se fija al crear la rifa para conservar los números generados.");
        }
        copiarDatos(rifa, request);
        rifa.setActualizadaEn(Instant.now(clock));
        return aDetalle(rifas.save(rifa));
    }

    @Transactional
    public void eliminar(Long id, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(id, actor);
        exigirBorrador(rifa);
        long vendidos = boletos.countByRifaIdAndEstado(id, EstadoBoleto.VENDIDO);
        long reservados = boletos.countByRifaIdAndEstado(id, EstadoBoleto.RESERVADO);
        if (vendidos > 0 || reservados > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una rifa con boletos vendidos o reservados.");
        }
        premios.deleteByRifaId(id);
        boletos.deleteByRifaId(id);
        rifas.delete(rifa);
    }

    @Transactional
    public RifaDtos.RifaDetalleDto publicar(Long id, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(id, actor);
        exigirBorrador(rifa);
        if (premios.countByRifaId(id) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un premio antes de publicar la rifa.");
        }
        Instant ahora = Instant.now(clock);
        if (!rifa.getCierreVentasEn().isAfter(ahora) || !rifa.getFechaSorteo().isAfter(rifa.getCierreVentasEn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las fechas de cierre y sorteo deben ser futuras y estar en orden.");
        }
        rifa.setEstado(EstadoRifa.PUBLICADA);
        rifa.setPublicadaEn(ahora);
        rifa.setActualizadaEn(ahora);
        return aDetalle(rifas.save(rifa));
    }

    @Transactional
    public RifaDtos.RifaDetalleDto cerrar(Long id, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(id, actor);
        if (rifa.getEstado() != EstadoRifa.PUBLICADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede cerrar una rifa publicada.");
        }
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setActualizadaEn(Instant.now(clock));
        return aDetalle(rifas.save(rifa));
    }

    @Transactional
    public RifaDtos.PremioDto crearPremio(Long rifaId, RifaDtos.PremioRequest request, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(rifaId, actor);
        exigirBorrador(rifa);
        if (premios.existsByRifaIdAndPosicion(rifaId, request.posicion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un premio con esa posición.");
        }
        Premio premio = new Premio();
        premio.setRifaId(rifaId);
        copiarDatos(premio, request);
        return aPremio(premios.save(premio));
    }

    @Transactional
    public RifaDtos.PremioDto actualizarPremio(Long rifaId, Long premioId, RifaDtos.PremioRequest request,
                                                UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(rifaId, actor);
        exigirBorrador(rifa);
        Premio premio = premios.findByIdAndRifaId(premioId, rifaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el premio."));
        if (premio.getPosicion() != request.posicion() && premios.existsByRifaIdAndPosicion(rifaId, request.posicion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un premio con esa posición.");
        }
        copiarDatos(premio, request);
        return aPremio(premios.save(premio));
    }

    @Transactional
    public void eliminarPremio(Long rifaId, Long premioId, UsuarioAutenticado actor) {
        Rifa rifa = rifaGestionable(rifaId, actor);
        exigirBorrador(rifa);
        Premio premio = premios.findByIdAndRifaId(premioId, rifaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el premio."));
        premios.delete(premio);
    }

    private Rifa rifaGestionable(Long id, UsuarioAutenticado actor) {
        Rifa rifa = rifas.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        if (actor.rol() != Rol.ADMIN && !rifa.getOrganizadorId().equals(actor.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el organizador propietario puede modificar esta rifa.");
        }
        return rifa;
    }

    private void validarPuedeOrganizar(UsuarioAutenticado actor) {
        if (actor.rol() != Rol.ADMIN && actor.rol() != Rol.ORGANIZADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el rol ORGANIZADOR.");
        }
        Usuario usuario = usuarios.findById(actor.id())
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no está activa."));
        if (actor.rol() != Rol.ADMIN && usuario.getRol() != Rol.ORGANIZADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no puede organizar rifas.");
        }
    }

    private void exigirBorrador(Rifa rifa) {
        if (rifa.getEstado() != EstadoRifa.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden editar rifas en estado BORRADOR.");
        }
    }

    private void validarFechas(Instant cierre, Instant sorteo) {
        Instant ahora = Instant.now(clock);
        if (cierre == null || sorteo == null || !cierre.isAfter(ahora) || !sorteo.isAfter(cierre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La fecha de cierre debe ser futura y la fecha del sorteo debe ser posterior al cierre.");
        }
    }

    private void validarCantidad(Integer cantidad, Integer maximo) {
        if (cantidad == null || cantidad < 10 || cantidad > MAX_BOLETOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La rifa debe tener entre 10 y " + MAX_BOLETOS + " boletos.");
        }
        if (maximo == null || maximo < 1 || maximo > cantidad) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El máximo de boletos por compra debe estar entre 1 y la cantidad total.");
        }
    }

    private void copiarDatos(Rifa rifa, RifaDtos.RifaRequest request) {
        rifa.setTitulo(request.titulo().trim());
        rifa.setDescripcion(limpiar(request.descripcion()));
        rifa.setImagenUrl(limpiar(request.imagenUrl()));
        rifa.setPrecioBoleto(request.precioBoleto());
        rifa.setMoneda(request.moneda() == null || request.moneda().isBlank() ? "COP" : request.moneda().toUpperCase(Locale.ROOT));
        rifa.setCantidadBoletos(request.cantidadBoletos());
        rifa.setFechaSorteo(request.fechaSorteo());
        rifa.setCierreVentasEn(request.cierreVentasEn());
        rifa.setMaximoBoletosPorCompra(request.maximoBoletosPorCompra());
        rifa.setMetodoSorteo(request.metodoSorteo());
        rifa.setLoteriaNombre(limpiar(request.loteriaNombre()));
        rifa.setNumeroAutorizacion(limpiar(request.numeroAutorizacion()));
        rifa.setEntidadAutorizadora(limpiar(request.entidadAutorizadora()));
    }

    private void copiarDatos(Premio premio, RifaDtos.PremioRequest request) {
        premio.setPosicion(request.posicion());
        premio.setNombre(request.nombre().trim());
        premio.setDescripcion(limpiar(request.descripcion()));
        premio.setValorEstimado(request.valorEstimado() == null ? BigDecimal.ZERO : request.valorEstimado());
        premio.setImagenUrl(limpiar(request.imagenUrl()));
    }

    private String limpiar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nuevoCodigo() {
        for (int intento = 0; intento < 5; intento++) {
            String codigo = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (!rifas.existsByCodigoPublico(codigo)) return codigo;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible generar un código público para la rifa.");
    }

    private void generarBoletos(Rifa rifa) {
        List<Boleto> lote = new ArrayList<>(TAMANO_LOTE);
        for (int numero = 0; numero < rifa.getCantidadBoletos(); numero++) {
            Boleto boleto = new Boleto();
            boleto.setRifaId(rifa.getId());
            boleto.setNumero(numero);
            boleto.setEstado(EstadoBoleto.DISPONIBLE);
            lote.add(boleto);
            if (lote.size() == TAMANO_LOTE) {
                boletos.saveAll(lote);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) boletos.saveAll(lote);
    }

    private RifaDtos.RifaResumenDto aResumen(Rifa rifa) {
        return new RifaDtos.RifaResumenDto(rifa.getCodigoPublico(), rifa.getTitulo(), rifa.getImagenUrl(),
            rifa.getPrecioBoleto(), rifa.getMoneda(), rifa.getCantidadBoletos(),
            boletos.countByRifaIdAndEstado(rifa.getId(), EstadoBoleto.DISPONIBLE), rifa.getEstado(),
            rifa.getFechaSorteo(), rifa.getCierreVentasEn());
    }

    private RifaDtos.RifaDetalleDto aDetalle(Rifa rifa) {
        long disponibles = boletos.countByRifaIdAndEstado(rifa.getId(), EstadoBoleto.DISPONIBLE);
        long reservados = boletos.countByRifaIdAndEstado(rifa.getId(), EstadoBoleto.RESERVADO);
        long vendidos = boletos.countByRifaIdAndEstado(rifa.getId(), EstadoBoleto.VENDIDO);
        String organizador = usuarios.findById(rifa.getOrganizadorId())
            .map(u -> u.getNombre() + " " + u.getApellido()).orElse("Organizador");
        List<RifaDtos.PremioDto> listaPremios = premios.findByRifaIdOrderByPosicionAsc(rifa.getId())
            .stream().map(this::aPremio).toList();
        int digitos = Math.max(1, String.valueOf(Math.max(0, rifa.getCantidadBoletos() - 1)).length());
        return new RifaDtos.RifaDetalleDto(rifa.getCodigoPublico(), rifa.getTitulo(), rifa.getDescripcion(),
            rifa.getImagenUrl(), rifa.getPrecioBoleto(), rifa.getMoneda(), rifa.getCantidadBoletos(), digitos,
            rifa.getEstado(), rifa.getFechaSorteo(), rifa.getCierreVentasEn(), rifa.getMaximoBoletosPorCompra(),
            rifa.getMetodoSorteo(), rifa.getLoteriaNombre(), rifa.getNumeroAutorizacion(),
            rifa.getEntidadAutorizadora(), rifa.getPublicadaEn(), organizador,
            new RifaDtos.Disponibilidad(disponibles, reservados, vendidos), listaPremios);
    }

    private RifaDtos.PremioDto aPremio(Premio premio) {
        return new RifaDtos.PremioDto(premio.getId(), premio.getPosicion(), premio.getNombre(),
            premio.getDescripcion(), premio.getValorEstimado(), premio.getImagenUrl());
    }

    private <T, R> RifaDtos.PaginaDto<R> pagina(Page<T> page, java.util.function.Function<T, R> mapper) {
        return new RifaDtos.PaginaDto<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
            page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private int paginaSegura(int pagina) {
        if (pagina < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La página debe ser 0 o mayor.");
        return pagina;
    }

    private int tamanoSeguro(int tamano) {
        if (tamano < 1 || tamano > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño de página debe estar entre 1 y 100.");
        return tamano;
    }
}
