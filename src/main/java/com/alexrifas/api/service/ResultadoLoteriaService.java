package com.alexrifas.api.service;

import com.alexrifas.api.config.LoteriasProperties;
import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.EstadoResultado;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.repository.ResultadoLoteriaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resultados de lotería cargados a mano (CP-08): cargar → verificar → (anular). Todo queda en
 * {@code auditoria_eventos}. Solo un resultado VERIFICADO podrá usarse para sortear una rifa (CP-09).
 */
@Service
public class ResultadoLoteriaService {
    static final int TAMANO_MAXIMO_PAGINA = 100;
    static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private final LoteriaRepository loterias;
    private final ResultadoLoteriaRepository resultados;
    private final AuditoriaService auditoria;
    private final LoteriasProperties propiedades;
    private final Clock clock;

    public ResultadoLoteriaService(LoteriaRepository loterias, ResultadoLoteriaRepository resultados,
                                   AuditoriaService auditoria, LoteriasProperties propiedades, Clock clock) {
        this.loterias = loterias;
        this.resultados = resultados;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    @Transactional
    public LoteriaDtos.ResultadoDto cargar(Long loteriaId, LoteriaDtos.CargarResultadoRequest request,
                                           UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        Loteria loteria = loterias.findById(loteriaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la lotería."));
        if (!loteria.isActiva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La lotería «" + loteria.getNombre() + "» está inactiva; actívala para cargar resultados.");
        }
        String numeroMayor = request.numeroMayor() == null ? "" : request.numeroMayor().trim();
        if (!numeroMayor.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El número mayor debe tener exactamente 4 dígitos, con ceros a la izquierda si aplica (ej. 0377).");
        }
        String serie = LoteriaService.textoOpcional(request.serie());
        if (serie != null && !serie.matches("\\d{1,3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La serie debe tener de 1 a 3 dígitos.");
        }
        String fuente = LoteriaService.textoOpcional(request.fuente());
        if (fuente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fuente del resultado.");
        }
        LocalDate hoy = LocalDate.now(clock.withZone(ZONA_COLOMBIA));
        if (request.fechaSorteo() == null || request.fechaSorteo().isAfter(hoy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La fecha del sorteo no puede ser futura: solo se cargan resultados de sorteos ya realizados.");
        }
        if (resultados.existsByLoteriaIdAndFechaSorteoAndEstadoNot(loteriaId, request.fechaSorteo(),
                EstadoResultado.ANULADO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya hay un resultado vigente de «" + loteria.getNombre() + "» para el " + request.fechaSorteo()
                    + ". Si estaba mal, anúlalo y vuelve a cargarlo.");
        }

        Instant ahora = Instant.now(clock);
        ResultadoLoteria resultado = new ResultadoLoteria();
        resultado.setLoteriaId(loteriaId);
        resultado.setFechaSorteo(request.fechaSorteo());
        resultado.setNumeroMayor(numeroMayor);
        resultado.setSerie(serie);
        resultado.setNumeroSorteo(request.numeroSorteo());
        resultado.setFuente(fuente);
        resultado.setEvidenciaUrl(LoteriaService.textoOpcional(request.evidenciaUrl()));
        resultado.setNotas(LoteriaService.textoOpcional(request.notas()));
        resultado.setEstado(EstadoResultado.PENDIENTE_VERIFICACION);
        resultado.setCargadoPor(actor.id());
        resultado.setCreadoEn(ahora);
        resultado.setActualizadoEn(ahora);
        ResultadoLoteria guardado = resultados.save(resultado);

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("loteria", loteria.getNombre());
        detalles.put("fechaSorteo", guardado.getFechaSorteo().toString());
        detalles.put("numeroMayor", guardado.getNumeroMayor());
        detalles.put("serie", guardado.getSerie());
        detalles.put("numeroSorteo", guardado.getNumeroSorteo());
        detalles.put("fuente", guardado.getFuente());
        detalles.put("evidenciaUrl", guardado.getEvidenciaUrl());
        auditoria.registrar(actor, "resultado_loteria", guardado.getId(), "RESULTADO_CARGADO", detalles);
        return aDto(guardado, loteria);
    }

    /** Confirma que el número coincide con la fuente oficial. Con segundo verificador exigido, no puede ser quien lo cargó. */
    @Transactional
    public LoteriaDtos.ResultadoDto verificar(Long id, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        ResultadoLoteria resultado = buscar(id);
        if (resultado.getEstado() != EstadoResultado.PENDIENTE_VERIFICACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, resultado.getEstado() == EstadoResultado.VERIFICADO
                ? "Este resultado ya está verificado." : "Este resultado está anulado; carga uno nuevo.");
        }
        boolean mismaPersona = actor.id() != null && actor.id().equals(resultado.getCargadoPor());
        if (propiedades.exigirSegundoVerificador() && mismaPersona) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Otro administrador debe verificar este resultado: quien lo cargó no puede verificarlo.");
        }
        Instant ahora = Instant.now(clock);
        resultado.setEstado(EstadoResultado.VERIFICADO);
        resultado.setVerificadoPor(actor.id());
        resultado.setVerificadoEn(ahora);
        resultado.setActualizadoEn(ahora);
        ResultadoLoteria guardado = resultados.save(resultado);

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("numeroMayor", guardado.getNumeroMayor());
        detalles.put("fechaSorteo", guardado.getFechaSorteo().toString());
        detalles.put("cargadoPor", guardado.getCargadoPor());
        detalles.put("mismaPersonaCargoYVerifico", mismaPersona);
        auditoria.registrar(actor, "resultado_loteria", guardado.getId(), "RESULTADO_VERIFICADO", detalles);
        return aDto(guardado, loteriaDe(guardado));
    }

    /** Anula (nunca borra) un resultado mal cargado, con motivo obligatorio. Para corregir, se anula y se carga de nuevo. */
    @Transactional
    public LoteriaDtos.ResultadoDto anular(Long id, LoteriaDtos.AnularResultadoRequest request, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        ResultadoLoteria resultado = buscar(id);
        if (resultado.getEstado() == EstadoResultado.ANULADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este resultado ya está anulado.");
        }
        String motivo = request == null ? null : LoteriaService.textoOpcional(request.motivo());
        if (motivo == null || motivo.length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo de la anulación es obligatorio.");
        }
        EstadoResultado anterior = resultado.getEstado();
        Instant ahora = Instant.now(clock);
        resultado.setEstado(EstadoResultado.ANULADO);
        resultado.setAnuladoPor(actor.id());
        resultado.setAnuladoEn(ahora);
        resultado.setMotivoAnulacion(motivo);
        resultado.setActualizadoEn(ahora);
        ResultadoLoteria guardado = resultados.save(resultado);

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("estadoAnterior", anterior.name());
        detalles.put("numeroMayor", guardado.getNumeroMayor());
        detalles.put("fechaSorteo", guardado.getFechaSorteo().toString());
        detalles.put("motivo", motivo);
        auditoria.registrar(actor, "resultado_loteria", guardado.getId(), "RESULTADO_ANULADO", detalles);
        return aDto(guardado, loteriaDe(guardado));
    }

    /** Más recientes primero. {@code estado} opcional (ej. PENDIENTE_VERIFICACION para la cola de verificación). */
    @Transactional(readOnly = true)
    public RifaDtos.Pagina<LoteriaDtos.ResultadoDto> listar(Long loteriaId, EstadoResultado estado, int pagina,
                                                            int tamano, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        Loteria loteria = loterias.findById(loteriaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la lotería."));
        PageRequest pedido = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA),
            Sort.by(Sort.Direction.DESC, "fechaSorteo").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<ResultadoLoteria> filas = estado == null ? resultados.findByLoteriaId(loteriaId, pedido)
            : resultados.findByLoteriaIdAndEstado(loteriaId, estado, pedido);
        return new RifaDtos.Pagina<>(filas.getContent().stream().map(r -> aDto(r, loteria)).toList(),
            filas.getNumber(), filas.getSize(), filas.getTotalElements(), filas.getTotalPages());
    }

    private ResultadoLoteria buscar(Long id) {
        return resultados.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el resultado."));
    }

    private Loteria loteriaDe(ResultadoLoteria resultado) {
        return loterias.findById(resultado.getLoteriaId()).orElse(null);
    }

    static LoteriaDtos.ResultadoDto aDto(ResultadoLoteria r, Loteria loteria) {
        return new LoteriaDtos.ResultadoDto(r.getId(), r.getLoteriaId(), loteria == null ? null : loteria.getNombre(),
            loteria == null ? null : loteria.getSitioOficialUrl(), r.getFechaSorteo(), r.getNumeroMayor(), r.getSerie(),
            r.getNumeroSorteo(), r.ultimosDigitos(2), r.ultimosDigitos(3), r.ultimosDigitos(4), r.getFuente(),
            r.getEvidenciaUrl(), r.getNotas(), r.getEstado(), r.getCargadoPor(), r.getCreadoEn(),
            r.getVerificadoPor(), r.getVerificadoEn(), r.getAnuladoPor(), r.getAnuladoEn(), r.getMotivoAnulacion());
    }
}
