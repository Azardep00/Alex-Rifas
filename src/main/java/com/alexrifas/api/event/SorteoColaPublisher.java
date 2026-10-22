package com.alexrifas.api.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SorteoColaPublisher {
    private static final Logger log = LoggerFactory.getLogger(SorteoColaPublisher.class);
    private static final String COLA = "cola:resultados";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SorteoColaPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(SorteoProcesadoEvent evento) {
        try {
            redis.opsForList().rightPush(COLA, mapper.writeValueAsString(evento.payload()));
            log.info("Evento de sorteo enviado a Redis para rifa {}", evento.payload().get("rifa_codigo"));
        } catch (Exception ex) {
            // La transacción de negocio ya quedó confirmada; se registra el fallo sin simular que hubo rollback.
            log.error("No fue posible enviar a Redis el resultado de la rifa {}. Debe reintentarse manualmente.",
                evento.payload().get("rifa_codigo"), ex);
        }
    }
}
