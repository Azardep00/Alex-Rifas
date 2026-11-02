package com.alexrifas.api.service;

import com.alexrifas.api.config.AntiabusoProperties;
import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.model.EstadoCompra;
import com.alexrifas.api.repository.CompraRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * Antiabuso de reservas sin cuenta (ADR-05): evita que alguien bloquee la grilla reservando sin pagar.
 *
 * <ul>
 *   <li><b>Por teléfono:</b> se cuentan las compras PENDIENTES vigentes en la base de datos (fuente de verdad).</li>
 *   <li><b>Por IP:</b> conjunto ordenado en Redis {@code abuso:ip:{ip}} con el código de cada reserva y su
 *       vencimiento como puntaje. Primero se agrega y luego se cuenta, para que dos peticiones simultáneas no
 *       se cuelen ambas por el mismo hueco. Si Redis falla, el tope por IP se omite (solo queda el del teléfono).</li>
 * </ul>
 */
@Service
public class AntiabusoService {
    private static final Logger log = LoggerFactory.getLogger(AntiabusoService.class);
    static final String CLAVE_IP = "abuso:ip:";
    static final String CLAVE_COMPRA = "abuso:compra:";
    private static final Duration MARGEN_TTL = Duration.ofMinutes(5);

    private final CompraRepository compras;
    private final StringRedisTemplate redis;
    private final AntiabusoProperties props;

    public AntiabusoService(CompraRepository compras, StringRedisTemplate redis, AntiabusoProperties props) {
        this.compras = compras;
        this.redis = redis;
        this.props = props;
    }

    /**
     * Verifica ambos topes y registra la reserva en el contador por IP.
     *
     * @throws ReservasPendientesExcedidasException (429) si se alcanzó algún tope
     */
    public void verificarYRegistrar(String codigoCompra, String telefono, String ip, Instant ahora, Instant vence) {
        verificarTelefono(telefono, ahora);
        registrarIp(codigoCompra, ip, ahora, vence);
    }

    /** Libera el cupo por IP de una reserva que dejó de estar pendiente (confirmada, cancelada o fallida). */
    public void liberar(String codigoCompra) {
        try {
            String ip = redis.opsForValue().getAndDelete(CLAVE_COMPRA + codigoCompra);
            if (ip != null) {
                redis.opsForZSet().remove(CLAVE_IP + ip, codigoCompra);
            }
        } catch (Exception ex) {
            log.warn("No se pudo liberar el cupo antiabuso de {}: {}", codigoCompra, ex.getMessage());
        }
    }

    private void verificarTelefono(String telefono, Instant ahora) {
        List<Compra> pendientes = compras.findByCompradorTelefonoAndEstadoAndExpiraEnAfterOrderByExpiraEnAsc(
            telefono, EstadoCompra.PENDIENTE, ahora);
        if (pendientes.size() >= props.maxPendientesPorTelefono()) {
            long segundos = Duration.between(ahora, pendientes.get(0).getExpiraEn()).toSeconds();
            throw new ReservasPendientesExcedidasException(
                "Ya tienes " + pendientes.size() + " reservas pendientes de pago. Págalas o espera a que venzan "
                    + "para reservar más; podrás intentarlo de nuevo " + cuando(segundos) + ".", segundos);
        }
    }

    private void registrarIp(String codigoCompra, String ip, Instant ahora, Instant vence) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        String clave = CLAVE_IP + ip;
        try {
            ZSetOperations<String, String> conjunto = redis.opsForZSet();
            conjunto.removeRangeByScore(clave, Double.NEGATIVE_INFINITY, ahora.toEpochMilli());
            conjunto.add(clave, codigoCompra, vence.toEpochMilli());
            Long total = conjunto.size(clave);
            if (total != null && total > props.maxPendientesPorIp()) {
                long segundos = segundosHastaElPrimerVencimiento(conjunto, clave, ahora, vence);
                conjunto.remove(clave, codigoCompra);
                throw new ReservasPendientesExcedidasException(
                    "Hay demasiadas reservas pendientes desde tu conexión. Paga las que ya hiciste o espera a que "
                        + "venzan; podrás intentarlo de nuevo " + cuando(segundos) + ".", segundos);
            }
            Duration vigencia = Duration.between(ahora, vence).plus(MARGEN_TTL);
            redis.expire(clave, vigencia);
            redis.opsForValue().set(CLAVE_COMPRA + codigoCompra, ip, vigencia);
        } catch (ReservasPendientesExcedidasException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Tope por IP no disponible (Redis): {}", ex.getMessage());
        }
    }

    private long segundosHastaElPrimerVencimiento(ZSetOperations<String, String> conjunto, String clave,
                                                  Instant ahora, Instant respaldo) {
        Set<ZSetOperations.TypedTuple<String>> primero = conjunto.rangeWithScores(clave, 0, 0);
        if (primero != null && !primero.isEmpty()) {
            Double puntaje = primero.iterator().next().getScore();
            if (puntaje != null) {
                return Duration.between(ahora, Instant.ofEpochMilli(puntaje.longValue())).toSeconds();
            }
        }
        return Duration.between(ahora, respaldo).toSeconds();
    }

    private static String cuando(long segundos) {
        long minutos = Math.max(1, (segundos + 59) / 60);
        return "en unos " + minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
