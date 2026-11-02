package com.alexrifas.api.service;

import com.alexrifas.api.entity.AuditoriaEvento;
import com.alexrifas.api.repository.AuditoriaEventoRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Registra en {@code auditoria_eventos} quién hizo qué. Se llama DENTRO de la transacción de la operación:
 * si la auditoría no se puede guardar, la operación tampoco (no existen acciones sin rastro).
 */
@Service
public class AuditoriaService {
    private final AuditoriaEventoRepository eventos;
    private final Clock clock;

    public AuditoriaService(AuditoriaEventoRepository eventos, Clock clock) {
        this.eventos = eventos;
        this.clock = clock;
    }

    public void registrar(UsuarioAutenticado actor, String entidad, Object entidadId, String accion,
                          Map<String, Object> detalles) {
        AuditoriaEvento evento = new AuditoriaEvento();
        evento.setUsuarioId(actor == null ? null : actor.id());
        evento.setEntidad(entidad);
        evento.setEntidadId(entidadId == null ? null : String.valueOf(entidadId));
        evento.setAccion(accion);
        evento.setRequestId(MDC.get("requestId"));
        evento.setDetalles(detalles);
        evento.setCreadoEn(Instant.now(clock));
        eventos.save(evento);
    }
}
