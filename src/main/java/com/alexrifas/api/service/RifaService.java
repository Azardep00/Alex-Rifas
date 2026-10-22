package com.alexrifas.api.service;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.repository.UsuarioRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ciclo de vida de una rifa: BORRADOR -> PUBLICADA -> CERRADA -> SORTEADA (o CANCELADA).
 * Al crearla se generan sus boletos, numerados de 0 a cantidadBoletos - 1, todos DISPONIBLES.
 */
@Service
public class RifaService {
    static final int MAX_BOLETOS = 50_000;
    private static final int MIN_BOLETOS = 10;
    private static final int LOTE_BOLETOS = 1_000;
    private static final int TAMANO_MAXIMO_PAGINA = 50;
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin 0/O/1/I
    private static final int LONGITUD_CODIGO = 8;
    private static final Set<EstadoRifa> VISIBLES_AL_PUBLICO =
        Set.of(EstadoRifa.PUBLICADA, EstadoRifa.CERRADA, EstadoRifa.SORTEADA);

    private final RifaRepository rifas;
    private final PremioRepository premios;
    private final BoletoRepository boletos;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    public RifaService(RifaRepository rifas, PremioRepository premios, BoletoRepository boletos,
                       UsuarioRepository usuarios, Clock clock) {
        this.rifas = rifas;
        this.premios = premios;
        this.boletos = boletos;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ gestión (organizador / admin)

    @Transactional
    public RifaDtos.RifaGestionDto crear(RifaDtos.RifaRequest request, UsuarioAutenticado actor) {
        exigirGestor(actor);
        usuarios.findById(actor.id())
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta no está activa."));
        Instant ahora = Instant.now(clock);
        validarRifa(request, ahora);

        Rifa rifa = new Rifa();
        rifa.setCodigoPublico(generarCodigoUnico());
        rifa.setOrganizadorId(actor.id());
        rifa.setTitulo(request.titulo().trim());
        rifa.setDescripcion(textoOpcional(request.descripcion()));
        rifa.setImagenUrl(textoOpcional(request.imagenUrl()));
        rifa.setPrecioBoleto(request.precioBoleto());
        rifa.setMoneda(request.moneda().trim().toUpperCase(Locale.ROOT));
        rifa.setCantidadBoletos(request.cantidadBoletos());
        rifa.setEstado(EstadoRifa.BORRADOR);
        rifa.setFechaSorteo(request.fechaSorteo());
        rifa.setCierreVentasEn(request.cierreVentasEn());
        rifa.setMaximoBoletosPorCompra(request.maximoBoletosPorCompra());
        rifa.setMetodoSorteo(request.metodoSorteo());
        rifa.setLoteriaNombre(textoOpcional(request.loteriaNombre()));
        rifa.setNumeroAutorizacion(textoOpcional(request.numeroAutorizacion()));
        rifa.setEntidadAutorizadora(textoOpcional(request.entidadAutorizadora()));
        rifa.setCreadaEn(ahora);
        rifa.setActualizadaEn(ahora);
        Rifa guardada = rifas.save(rifa);

        generarBoletos(guardada);
        return gestion(guardada);
    }

    @Transactional
    public RifaDtos.PremioDto agregarPremio(Long rifaId, RifaDtos.PremioRequest request, UsuarioAutenticado actor) {
        Rifa rifa = buscarParaGestion(rifaId, actor);
        if (rifa.getEstado() != EstadoRifa.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo puedes agregar premios mientras la rifa está en borrador.");
        }
        if (request.posicion() == null || request.posicion() < 1) {
            throw solicitudInvalida("La posición del premio debe ser al menos 1.");
        }
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw solicitudInvalida("El nombre del premio es obligatorio.");
        }
        BigDecimal valor = request.valorEstimado() == null ? BigDecimal.ZERO : request.valorEstimado();
        if (valor.signum() < 0) {
            throw solicitudInvalida("El valor estimado no puede ser negativo.");
        }
        validarUrlImagen(request.imagenUrl());
        if (premios.existsByRifaIdAndPosicion(rifaId, request.posicion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un premio en la posición " + request.posicion() + ".");
        }
        if (premios.countByRifaId(rifaId) >= rifa.getCantidadBoletos()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La rifa no puede tener más premios que boletos.");
        }

        Premio premio = new Premio();
        premio.setRifaId(rifaId);
        premio.setPosicion(request.posicion());
        premio.setNombre(request.nombre().trim());
        premio.setDescripcion(textoOpcional(request.descripcion()));
        premio.setValorEstimado(valor);
        premio.setImagenUrl(textoOpcional(request.imagenUrl()));
        return premioDto(premios.save(premio));
    }

    @Transactional
    public RifaDtos.RifaGestionDto publicar(Long rifaId, UsuarioAutenticado actor) {
        Rifa rifa = buscarParaGestion(rifaId, actor);
        if (rifa.getEstado() != EstadoRifa.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede publicar una rifa en borrador.");
        }
        if (premios.countByRifaId(rifaId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agrega al menos un premio antes de publicar.");
        }
        Instant ahora = Instant.now(clock);
        if (!rifa.getCierreVentasEn().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La fecha de cierre de ventas ya pasó; crea la rifa con nuevas fechas.");
        }
        rifa.setEstado(EstadoRifa.PUBLICADA);
        rifa.setPublicadaEn(ahora);
        rifa.setActualizadaEn(ahora);
        return gestion(rifas.save(rifa));
    }

    /** Cierra las ventas (necesario antes de sortear). */
    @Transactional
    public RifaDtos.RifaGestionDto cerrar(Long rifaId, UsuarioAutenticado actor) {
        Rifa rifa = buscarParaGestion(rifaId, actor);
        if (rifa.getEstado() != EstadoRifa.PUBLICADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede cerrar una rifa publicada.");
        }
        rifa.setEstado(EstadoRifa.CERRADA);
        rifa.setActualizadaEn(Instant.now(clock));
        return gestion(rifas.save(rifa));
    }

    @Transactional
    public RifaDtos.RifaGestionDto cancelar(Long rifaId, UsuarioAutenticado actor) {
        Rifa rifa = buscarParaGestion(rifaId, actor);
        if (rifa.getEstado() == EstadoRifa.SORTEADA || rifa.getEstado() == EstadoRifa.CANCELADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta rifa ya no se puede cancelar.");
        }
        if (boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.VENDIDO) > 0
                || boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.RESERVADO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede cancelar una rifa con boletos reservados o vendidos.");
        }
        rifa.setEstado(EstadoRifa.CANCELADA);
        rifa.setActualizadaEn(Instant.now(clock));
        return gestion(rifas.save(rifa));
    }

    /** Un organizador ve sus rifas; un administrador ve todas. */
    @Transactional(readOnly = true)
    public RifaDtos.Pagina<RifaDtos.RifaGestionDto> misRifas(UsuarioAutenticado actor, int pagina, int tamano) {
        exigirGestor(actor);
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);
        Page<Rifa> resultado;
        if (actor.rol() == Rol.ADMIN) {
            Pageable pageable = PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "creadaEn"));
            resultado = rifas.findAll(pageable);
        } else {
            resultado = rifas.findByOrganizadorIdOrderByCreadaEnDesc(actor.id(), PageRequest.of(p, t));
        }
        return paginaDe(resultado, resultado.getContent().stream().map(this::gestion).toList());
    }

    // ------------------------------------------------------------------ consulta pública

    @Transactional(readOnly = true)
    public RifaDtos.Pagina<RifaDtos.RifaResumen> listarPublicas(int pagina, int tamano) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);
        Page<Rifa> resultado = rifas.findByEstadoOrderByFechaSorteoAsc(EstadoRifa.PUBLICADA, PageRequest.of(p, t));
        List<RifaDtos.RifaResumen> contenido = resultado.getContent().stream()
            .map(rifa -> new RifaDtos.RifaResumen(rifa.getCodigoPublico(), rifa.getTitulo(), rifa.getImagenUrl(),
                rifa.getEstado(), rifa.getPrecioBoleto(), rifa.getMoneda(), rifa.getCantidadBoletos(),
                boletos.countByRifaIdAndEstado(rifa.getId(), EstadoBoleto.DISPONIBLE), rifa.getFechaSorteo()))
            .toList();
        return paginaDe(resultado, contenido);
    }

    @Transactional(readOnly = true)
    public RifaDtos.RifaDetalle detallePublico(String codigo) {
        Rifa rifa = rifas.findByCodigoPublico(codigo.trim().toUpperCase(Locale.ROOT))
            .filter(r -> VISIBLES_AL_PUBLICO.contains(r.getEstado()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        List<RifaDtos.PremioDto> listaPremios = premios.findByRifaIdOrderByPosicionAsc(rifa.getId()).stream()
            .map(this::premioDto).toList();
        return new RifaDtos.RifaDetalle(rifa.getCodigoPublico(), rifa.getTitulo(), rifa.getDescripcion(),
            rifa.getImagenUrl(), rifa.getEstado(), rifa.getPrecioBoleto(), rifa.getMoneda(),
            rifa.getCantidadBoletos(), rifa.getFechaSorteo(), rifa.getCierreVentasEn(),
            rifa.getMaximoBoletosPorCompra(), rifa.getMetodoSorteo(), rifa.getLoteriaNombre(),
            nombreOrganizador(rifa.getOrganizadorId()), disponibilidad(rifa.getId()), listaPremios);
    }

    // ------------------------------------------------------------------ validaciones y utilidades

    private void validarRifa(RifaDtos.RifaRequest r, Instant ahora) {
        if (r == null) {
            throw solicitudInvalida("La solicitud está vacía.");
        }
        if (r.titulo() == null || r.titulo().isBlank() || r.titulo().trim().length() > 120) {
            throw solicitudInvalida("El título es obligatorio y no puede superar 120 caracteres.");
        }
        if (r.descripcion() != null && r.descripcion().length() > 4000) {
            throw solicitudInvalida("La descripción no puede superar 4000 caracteres.");
        }
        validarUrlImagen(r.imagenUrl());
        if (r.precioBoleto() == null || r.precioBoleto().signum() <= 0) {
            throw solicitudInvalida("El precio del boleto debe ser mayor que cero.");
        }
        if (r.moneda() == null || !r.moneda().trim().toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) {
            throw solicitudInvalida("La moneda debe tener 3 letras (por ejemplo COP).");
        }
        if (r.cantidadBoletos() == null || r.cantidadBoletos() < MIN_BOLETOS) {
            throw solicitudInvalida("La rifa debe tener al menos " + MIN_BOLETOS + " boletos.");
        }
        if (r.cantidadBoletos() > MAX_BOLETOS) {
            throw solicitudInvalida("La rifa no puede tener más de " + MAX_BOLETOS + " boletos.");
        }
        if (r.cierreVentasEn() == null || !r.cierreVentasEn().isAfter(ahora)) {
            throw solicitudInvalida("El cierre de ventas debe ser una fecha futura.");
        }
        if (r.fechaSorteo() == null || !r.fechaSorteo().isAfter(r.cierreVentasEn())) {
            throw solicitudInvalida("La fecha del sorteo debe ser posterior al cierre de ventas.");
        }
        if (r.maximoBoletosPorCompra() == null || r.maximoBoletosPorCompra() < 1
                || r.maximoBoletosPorCompra() > r.cantidadBoletos()) {
            throw solicitudInvalida("El máximo de boletos por compra debe estar entre 1 y la cantidad de boletos.");
        }
        if (r.metodoSorteo() == null) {
            throw solicitudInvalida("El método de sorteo es obligatorio.");
        }
        if (r.metodoSorteo() == MetodoSorteo.LOTERIA
                && (r.loteriaNombre() == null || r.loteriaNombre().isBlank())) {
            throw solicitudInvalida("Indica el nombre de la lotería con la que se definirá el sorteo.");
        }
    }

    /** Las imágenes se muestran en el sitio web, cuyo CSP solo admite https. */
    private void validarUrlImagen(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (url.length() > 500 || !url.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw solicitudInvalida("La URL de la imagen debe empezar por https:// y tener máximo 500 caracteres.");
        }
    }

    private void generarBoletos(Rifa rifa) {
        List<Boleto> lote = new ArrayList<>(LOTE_BOLETOS);
        for (int numero = 0; numero < rifa.getCantidadBoletos(); numero++) {
            Boleto boleto = new Boleto();
            boleto.setRifaId(rifa.getId());
            boleto.setNumero(numero);
            boleto.setEstado(EstadoBoleto.DISPONIBLE);
            lote.add(boleto);
            if (lote.size() == LOTE_BOLETOS) {
                boletos.saveAll(lote);
                lote = new ArrayList<>(LOTE_BOLETOS);
            }
        }
        if (!lote.isEmpty()) {
            boletos.saveAll(lote);
        }
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder codigo = new StringBuilder(LONGITUD_CODIGO);
            for (int i = 0; i < LONGITUD_CODIGO; i++) {
                codigo.append(ALFABETO_CODIGO.charAt(aleatorio.nextInt(ALFABETO_CODIGO.length())));
            }
            if (!rifas.existsByCodigoPublico(codigo.toString())) {
                return codigo.toString();
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "No fue posible generar un código único para la rifa. Intenta de nuevo.");
    }

    private void exigirGestor(UsuarioAutenticado actor) {
        if (actor == null || (actor.rol() != Rol.ORGANIZADOR && actor.rol() != Rol.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo los organizadores y administradores pueden gestionar rifas.");
        }
    }

    private Rifa buscarParaGestion(Long rifaId, UsuarioAutenticado actor) {
        exigirGestor(actor);
        Rifa rifa = rifas.findById(rifaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        if (actor.rol() != Rol.ADMIN && !actor.id().equals(rifa.getOrganizadorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta rifa pertenece a otro organizador.");
        }
        return rifa;
    }

    private RifaDtos.Disponibilidad disponibilidad(Long rifaId) {
        return new RifaDtos.Disponibilidad(
            boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.DISPONIBLE),
            boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.RESERVADO),
            boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.VENDIDO));
    }

    private RifaDtos.RifaGestionDto gestion(Rifa rifa) {
        return new RifaDtos.RifaGestionDto(rifa.getId(), rifa.getCodigoPublico(), rifa.getTitulo(),
            rifa.getEstado(), rifa.getPrecioBoleto(), rifa.getMoneda(), rifa.getCantidadBoletos(),
            rifa.getFechaSorteo(), rifa.getCierreVentasEn(), rifa.getMaximoBoletosPorCompra(),
            rifa.getMetodoSorteo(), rifa.getNumeroAutorizacion(), rifa.getEntidadAutorizadora(),
            disponibilidad(rifa.getId()));
    }

    private RifaDtos.PremioDto premioDto(Premio premio) {
        return new RifaDtos.PremioDto(premio.getPosicion(), premio.getNombre(), premio.getDescripcion(),
            premio.getValorEstimado(), premio.getImagenUrl());
    }

    /** En público solo se muestra el nombre y la inicial del apellido. */
    private String nombreOrganizador(Long organizadorId) {
        return usuarios.findById(organizadorId)
            .map(u -> {
                String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
                return apellido.isEmpty() ? u.getNombre() : u.getNombre() + " " + apellido.charAt(0) + ".";
            })
            .orElse("Organizador de Alex-Rifas");
    }

    private <T> RifaDtos.Pagina<T> paginaDe(Page<Rifa> origen, List<T> contenido) {
        return new RifaDtos.Pagina<>(contenido, origen.getNumber(), origen.getSize(),
            origen.getTotalElements(), origen.getTotalPages());
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private ResponseStatusException solicitudInvalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
