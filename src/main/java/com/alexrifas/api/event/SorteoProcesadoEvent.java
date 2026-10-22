package com.alexrifas.api.event;

import java.util.Map;

/** Evento interno publicado solamente después de confirmar la transacción del sorteo. */
public record SorteoProcesadoEvent(Map<String, Object> payload) {}
