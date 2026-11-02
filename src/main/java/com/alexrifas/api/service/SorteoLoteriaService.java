package com.alexrifas.api.service;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Ganador;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.event.SorteoProcesadoEvent;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.GanadorRepository;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.ResultadoLoteriaRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sorteo de una rifa con el resultado de una lotería (CP-09). Solo ADMIN: nadie escoge el número, lo deciden los
 * resultados VERIFICADOS de la lotería de la rifa, uno por premio y en orden de fecha (reglas en
 * {@link SorteoLoteriaCalculador}).
 *
 * <ul>
 *   <li>{@link #previsualizar} no guarda nada y puede responder "incompleto" (falta o está sin verificar un resultado).</li>
 *   <li>{@link #ejecutar} es atómico: o decide todos los premios y deja la rifa SORTEADA, o no guarda nada.</li>
 * </ul>
 */
@Service
public class SorteoLoteriaService {
    private final RifaRepository rifas;
    private final PremioRepository premios;
    private final BoletoRepository boletos;
    private final GanadorRepository ganadores;
    private final LoteriaRepository loterias;
    private final ResultadoLoteriaRepository resultados;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public SorteoLoteriaService(RifaRepository rifas, PremioRepository premios, BoletoRepository boletos,
                                GanadorRepository ganadores, LoteriaRepository loterias,
                                ResultadoLoteriaRepository resultados, AuditoriaService auditoria,
                                ApplicationEventPublisher eventos, Clock clock) {
        this.rifas = rifas;
        this.premios = premios;
        this.boletos = boletos;
        this.ganadores = ganadores;
        this.loterias = loterias;
        this.resultados = resultados;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SorteoDtos.SorteoLoteriaDto previsualizar(Long rifaId, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        Contexto ctx = preparar(rifaId, false);
        return aDto(ctx, false);
    }

    @Transactional
    public SorteoDtos.SorteoLoteriaDto ejecutar(Long rifaId, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        // Resultados bloqueados: nadie puede verificarlos ni anularlos mientras se decide el sorteo.
        Contexto ctx = preparar(rifaId, true);
        if (!ctx.plan().completo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, mensajeBloqueo(ctx));
        }

        Rifa rifa = ctx.rifa();
        Instant ahora = Instant.now(clock);
        List<Ganador> nuevos = new ArrayList<>();
        List<Map<String, Object>> ganadoresCola = new ArrayList<>();
        for (SorteoLoteriaCalculador.Asignacion a : ctx.plan().asignaciones()) {
            Ganador ganador = new Ganador();
            ganador.setRifaId(rifa.getId());
            ganador.setPremioId(a.premio().getId());
            ganador.setBoletoId(a.boleto().getId());
            ganador.setPublicado(true);
            ganador.setResultadoJson(null);
            ganador.setCreadoEn(ahora);
            ganador.setResultadoLoteriaId(a.resultado().getId());
            ganador.setNumeroResultado(a.numeroObjetivo());
            ganador.setAdjudicadoPorVacante(a.porVacante());
            nuevos.add(ganador);
            ganadoresCola.add(Map.of("premio", a.premio().getNombre(), "numero", a.boleto().getNumero()));
        }
        ganadores.saveAll(nuevos);
        rifa.setEstado(EstadoRifa.SORTEADA);
        rifa.setActualizadaEn(ahora);
        rifas.save(rifa);

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("rifa", rifa.getCodigoPublico());
        detalles.put("loteria", ctx.loteria().getNombre());
        detalles.put("digitos", ctx.digitos());
        detalles.put("reglaVacante", rifa.getReglaVacante().name());
        detalles.put("boletosVendidos", ctx.vendidos().size());
        detalles.put("pasos", ctx.plan().pasos().stream().map(p -> pasoAuditoria(p, ctx.digitos())).toList());
        auditoria.registrar(actor, "rifa", rifa.getId(), "SORTEO_LOTERIA_EJECUTADO", detalles);

        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("rifa_codigo", rifa.getCodigoPublico());
        mensaje.put("titulo", rifa.getTitulo());
        mensaje.put("cantidad_boletos", rifa.getCantidadBoletos());
        mensaje.put("boletos_vendidos", ctx.vendidos().size());
        mensaje.put("ganadores", ganadoresCola);
        eventos.publishEvent(new SorteoProcesadoEvent(mensaje));
        return aDto(ctx, true);
    }

    // ------------------------------------------------------------------ interno

    private record Contexto(Rifa rifa, Loteria loteria, int digitos, List<Boleto> vendidos,
                            SorteoLoteriaCalculador.Plan plan) {}

    /** Valida que la rifa esté lista para sortear y calcula el plan. {@code bloquear} = leer los resultados con lock. */
    private Contexto preparar(Long rifaId, boolean bloquear) {
        Rifa rifa = rifas.findById(rifaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        if (!rifa.sorteaPorLoteria()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta rifa no está configurada para sortearse por lotería (necesita método LOTERIA, lotería y dígitos).");
        }
        int digitos = rifa.getDigitos();
        if (digitos < 2 || digitos > 4 || rifa.getCantidadBoletos() != (int) Math.pow(10, digitos)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La configuración de la rifa es inconsistente: con " + digitos + " dígitos debe tener 10^" + digitos
                    + " boletos.");
        }
        if (ganadores.existsByRifaId(rifaId) || rifa.getEstado() == EstadoRifa.SORTEADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta rifa ya tiene un sorteo registrado.");
        }
        if (rifa.getEstado() != EstadoRifa.CERRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cierra la rifa antes de ejecutar el sorteo.");
        }
        if (boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.RESERVADO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Hay boletos reservados; espera a que las reservas expiren o se confirmen.");
        }
        List<Premio> listaPremios = premios.findByRifaIdOrderByPosicionAsc(rifaId);
        if (listaPremios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La rifa no tiene premios registrados.");
        }
        List<Boleto> vendidos = boletos.findByRifaIdAndEstadoOrderByNumeroAsc(rifaId, EstadoBoleto.VENDIDO);
        if (vendidos.size() < listaPremios.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No hay suficientes boletos vendidos para asignar todos los premios.");
        }
        Loteria loteria = loterias.findById(rifa.getLoteriaId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La lotería de la rifa ya no existe."));

        // Los resultados se consumen desde la fecha del sorteo de la rifa (en hora de Colombia), del más antiguo al más nuevo.
        LocalDate desde = rifa.getFechaSorteo().atZone(ResultadoLoteriaService.ZONA_COLOMBIA).toLocalDate();
        List<ResultadoLoteria> cadena = bloquear
            ? resultados.findVigentesDesdeParaActualizar(loteria.getId(), desde)
            : resultados.findVigentesDesde(loteria.getId(), desde);
        SorteoLoteriaCalculador.Plan plan =
            SorteoLoteriaCalculador.calcular(listaPremios, digitos, rifa.getReglaVacante(), vendidos, cadena);
        return new Contexto(rifa, loteria, digitos, vendidos, plan);
    }

    private SorteoDtos.SorteoLoteriaDto aDto(Contexto ctx, boolean ejecutado) {
        int d = ctx.digitos();
        List<SorteoDtos.PasoLoteriaDto> pasos = ctx.plan().pasos().stream().map(p -> new SorteoDtos.PasoLoteriaDto(
            p.posicionPremio(), p.resultado().getId(), p.resultado().getFechaSorteo(), p.resultado().getNumeroMayor(),
            relleno(p.numeroObjetivo(), d), p.resolucion().name(), p.causa() == null ? null : p.causa().name(),
            p.boleto() == null ? null : relleno(p.boleto().getNumero(), d))).toList();
        List<SorteoDtos.GanadorLoteriaDto> ganadoresDto = ctx.plan().asignaciones().stream()
            .map(a -> new SorteoDtos.GanadorLoteriaDto(a.premio().getPosicion(), a.premio().getNombre(),
                a.boleto().getNumero(), relleno(a.boleto().getNumero(), d), a.resultado().getId(),
                relleno(a.numeroObjetivo(), d), a.porVacante())).toList();
        String mensaje = ejecutado ? "Sorteo ejecutado: la rifa quedó SORTEADA."
            : ctx.plan().completo() ? "Todo listo: ejecuta el sorteo para publicar los ganadores." : mensajeBloqueo(ctx);
        return new SorteoDtos.SorteoLoteriaDto(ctx.rifa().getCodigoPublico(), ctx.rifa().getTitulo(),
            ctx.loteria().getNombre(), d, ctx.rifa().getReglaVacante().name(), ctx.plan().completo(), ejecutado,
            mensaje, pasos, ganadoresDto);
    }

    private String mensajeBloqueo(Contexto ctx) {
        SorteoLoteriaCalculador.Plan plan = ctx.plan();
        String nombre = ctx.loteria().getNombre();
        return switch (plan.bloqueo()) {
            case RESULTADO_PENDIENTE -> "El resultado de «" + nombre + "» del " + plan.pendiente().getFechaSorteo()
                + " está pendiente de verificación; verifícalo para decidir el premio " + plan.premioBloqueado() + ".";
            case FALTA_RESULTADO -> "Falta un resultado verificado de «" + nombre + "» para decidir el premio "
                + plan.premioBloqueado() + ": carga y verifica el del siguiente sorteo.";
            case NINGUNO -> "";
        };
    }

    private Map<String, Object> pasoAuditoria(SorteoLoteriaCalculador.Paso p, int digitos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("posicionPremio", p.posicionPremio());
        m.put("resultadoId", p.resultado().getId());
        m.put("fechaSorteoLoteria", p.resultado().getFechaSorteo().toString());
        m.put("numeroMayor", p.resultado().getNumeroMayor());
        m.put("numeroObjetivo", relleno(p.numeroObjetivo(), digitos));
        m.put("resolucion", p.resolucion().name());
        m.put("causa", p.causa() == null ? null : p.causa().name());
        m.put("numeroBoleto", p.boleto() == null ? null : relleno(p.boleto().getNumero(), digitos));
        return m;
    }

    private static String relleno(int numero, int digitos) {
        return String.format("%0" + digitos + "d", numero);
    }
}
