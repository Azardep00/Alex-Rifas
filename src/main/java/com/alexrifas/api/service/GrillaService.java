package com.alexrifas.api.service;

import com.alexrifas.api.dto.RifaDtos;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Estado de todos los números de una rifa en un solo arreglo compacto, con caché corta en Redis. */
@Service
public class GrillaService {
    private static final Logger log = LoggerFactory.getLogger(GrillaService.class);
    private static final Set<EstadoRifa> VISIBLES = Set.of(EstadoRifa.PUBLICADA, EstadoRifa.CERRADA, EstadoRifa.SORTEADA);
    static final Duration TTL_CACHE = Duration.ofSeconds(3);

    private final RifaRepository rifas;
    private final BoletoRepository boletos;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;

    public GrillaService(RifaRepository rifas, BoletoRepository boletos, StringRedisTemplate redis,
                         ObjectMapper mapper, Clock clock) {
        this.rifas = rifas;
        this.boletos = boletos;
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RifaDtos.GrillaBoletos grilla(String codigo) {
        String codigoNormalizado = codigo.trim().toUpperCase(Locale.ROOT);
        Rifa rifa = rifas.findByCodigoPublico(codigoNormalizado)
            .filter(r -> VISIBLES.contains(r.getEstado()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        String clave = "grilla:" + rifa.getCodigoPublico();

        RifaDtos.GrillaBoletos enCache = leerCache(clave);
        if (enCache != null) {
            return enCache;
        }
        RifaDtos.GrillaBoletos nueva = construir(rifa, boletos.findOcupadosByRifaId(rifa.getId()), Instant.now(clock));
        escribirCache(clave, nueva);
        return nueva;
    }

    /** Separado para poder probarlo sin base de datos. Una reserva vencida cuenta como libre. */
    RifaDtos.GrillaBoletos construir(Rifa rifa, List<BoletoRepository.OcupadoView> ocupados, Instant ahora) {
        char[] estados = new char[rifa.getCantidadBoletos()];
        Arrays.fill(estados, 'L');
        int reservados = 0;
        int vendidos = 0;
        for (BoletoRepository.OcupadoView b : ocupados) {
            if (b.getNumero() < 0 || b.getNumero() >= estados.length) {
                continue;
            }
            if (b.getEstado() == EstadoBoleto.VENDIDO) {
                estados[b.getNumero()] = 'V';
                vendidos++;
            } else if (b.getEstado() == EstadoBoleto.RESERVADO
                    && b.getReservadoHasta() != null && b.getReservadoHasta().isAfter(ahora)) {
                estados[b.getNumero()] = 'R';
                reservados++;
            }
        }
        return new RifaDtos.GrillaBoletos(rifa.getCodigoPublico(), estados.length, rifa.getDigitos(),
            new String(estados), estados.length - reservados - vendidos, reservados, vendidos, ahora);
    }

    // La caché es una optimización: si Redis falla, se responde desde la base de datos.
    private RifaDtos.GrillaBoletos leerCache(String clave) {
        try {
            String json = redis.opsForValue().get(clave);
            return json == null ? null : mapper.readValue(json, RifaDtos.GrillaBoletos.class);
        } catch (Exception ex) {
            log.warn("Caché de grilla no disponible ({}): {}", clave, ex.getMessage());
            return null;
        }
    }

    private void escribirCache(String clave, RifaDtos.GrillaBoletos grilla) {
        try {
            redis.opsForValue().set(clave, mapper.writeValueAsString(grilla), TTL_CACHE);
        } catch (Exception ex) {
            log.warn("No se pudo guardar la grilla en caché ({}): {}", clave, ex.getMessage());
        }
    }
}
