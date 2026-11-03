package com.alexrifas.api.service;

import com.alexrifas.api.dto.LoteriaDtos;
import com.alexrifas.api.entity.Loteria;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.LoteriaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Catálogo de loterías y chances contra los que se juegan las rifas (CP-08). Solo administradores. */
@Service
public class LoteriaService {
    private final LoteriaRepository loterias;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public LoteriaService(LoteriaRepository loterias, AuditoriaService auditoria, Clock clock) {
        this.loterias = loterias;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LoteriaDtos.LoteriaDto> listar(Boolean activa, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        List<Loteria> filas = activa == null ? loterias.findAllByOrderByNombreAsc()
            : loterias.findByActivaOrderByNombreAsc(activa);
        return filas.stream().map(LoteriaService::aDto).toList();
    }

    /** CP-17: el wizard de rifas necesita elegir lotería también como ORGANIZADOR. Solo lectura y solo activas. */
    @Transactional(readOnly = true)
    public List<LoteriaDtos.LoteriaDto> listarActivasParaGestor(UsuarioAutenticado actor) {
        if (actor == null || (actor.rol() != Rol.ADMIN && actor.rol() != Rol.ORGANIZADOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo organizadores y administradores.");
        }
        return loterias.findByActivaOrderByNombreAsc(true).stream().map(LoteriaService::aDto).toList();
    }

    @Transactional
    public LoteriaDtos.LoteriaDto crear(LoteriaDtos.LoteriaRequest request, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        String nombre = nombreLimpio(request.nombre());
        if (loterias.existsByNombreIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una lotería llamada «" + nombre + "».");
        }
        Instant ahora = Instant.now(clock);
        Loteria loteria = new Loteria();
        aplicar(loteria, request, nombre);
        loteria.setActiva(request.activa() == null || request.activa());
        loteria.setCreadaEn(ahora);
        loteria.setActualizadaEn(ahora);
        Loteria guardada = loterias.save(loteria);
        auditoria.registrar(actor, "loteria", guardada.getId(), "LOTERIA_CREADA", Map.of("despues", instantanea(guardada)));
        return aDto(guardada);
    }

    @Transactional
    public LoteriaDtos.LoteriaDto actualizar(Long id, LoteriaDtos.LoteriaRequest request, UsuarioAutenticado actor) {
        SoloAdmin.exigir(actor);
        Loteria loteria = loterias.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la lotería."));
        String nombre = nombreLimpio(request.nombre());
        if (loterias.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otra lotería llamada «" + nombre + "».");
        }
        Map<String, Object> antes = instantanea(loteria);
        aplicar(loteria, request, nombre);
        if (request.activa() != null) {
            loteria.setActiva(request.activa());
        }
        loteria.setActualizadaEn(Instant.now(clock));
        Loteria guardada = loterias.save(loteria);
        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("antes", antes);
        detalles.put("despues", instantanea(guardada));
        auditoria.registrar(actor, "loteria", guardada.getId(), "LOTERIA_ACTUALIZADA", detalles);
        return aDto(guardada);
    }

    private static void aplicar(Loteria loteria, LoteriaDtos.LoteriaRequest request, String nombre) {
        loteria.setNombre(nombre);
        loteria.setTipo(request.tipo());
        loteria.setDiasSemana(diasComoTexto(request.diasSemana()));
        loteria.setHoraSorteo(request.horaSorteo());
        loteria.setSitioOficialUrl(textoOpcional(request.sitioOficialUrl()));
    }

    private static Map<String, Object> instantanea(Loteria l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", l.getNombre());
        m.put("tipo", l.getTipo().name());
        m.put("diasSemana", l.getDiasSemana());
        m.put("horaSorteo", l.getHoraSorteo() == null ? null : l.getHoraSorteo().toString());
        m.put("sitioOficialUrl", l.getSitioOficialUrl());
        m.put("activa", l.isActiva());
        return m;
    }

    static LoteriaDtos.LoteriaDto aDto(Loteria l) {
        return new LoteriaDtos.LoteriaDto(l.getId(), l.getNombre(), l.getTipo(), diasComoLista(l.getDiasSemana()),
            l.getHoraSorteo(), l.getSitioOficialUrl(), l.isActiva());
    }

    /** Quita espacios sobrantes (incluidos los repetidos en medio) para que "Lotería  de Bogotá " no duplique. */
    private static String nombreLimpio(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio.");
        }
        return limpio;
    }

    static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** [4, 1, 4] → "1,4": sin repetidos y en orden. Lista vacía o nula → sin definir. */
    private static String diasComoTexto(List<Integer> dias) {
        if (dias == null || dias.isEmpty()) {
            return null;
        }
        for (Integer d : dias) {
            if (d == null || d < 1 || d > 7) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días van de 1 (lunes) a 7 (domingo).");
            }
        }
        return dias.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Integer> diasComoLista(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split(",")).map(Integer::valueOf).toList();
    }
}
