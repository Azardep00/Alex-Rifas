package com.alexrifas.api.service;

import com.alexrifas.api.dto.SorteoDtos;
import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.entity.Ganador;
import com.alexrifas.api.entity.Premio;
import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.event.SorteoProcesadoEvent;
import com.alexrifas.api.model.EstadoBoleto;
import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.BoletoRepository;
import com.alexrifas.api.repository.GanadorRepository;
import com.alexrifas.api.repository.PremioRepository;
import com.alexrifas.api.repository.RifaRepository;
import com.alexrifas.api.security.UsuarioAutenticado;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SorteoService {
    private final RifaRepository rifas;
    private final PremioRepository premios;
    private final BoletoRepository boletos;
    private final GanadorRepository ganadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    public SorteoService(RifaRepository rifas, PremioRepository premios, BoletoRepository boletos,
                         GanadorRepository ganadores, ApplicationEventPublisher eventos, Clock clock) {
        this.rifas = rifas;
        this.premios = premios;
        this.boletos = boletos;
        this.ganadores = ganadores;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public List<SorteoDtos.GanadorDto> sortear(Long rifaId, SorteoDtos.SorteoRequest request,
                                               UsuarioAutenticado actor) {
        Rifa rifa = rifas.findById(rifaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        exigirPropietario(rifa, actor);
        if (rifa.getEstado() != EstadoRifa.CERRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cierra la rifa antes de ejecutar el sorteo.");
        }
        if (ganadores.existsByRifaId(rifaId) || rifa.getEstado() == EstadoRifa.SORTEADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta rifa ya tiene un sorteo registrado.");
        }
        if (boletos.countByRifaIdAndEstado(rifaId, EstadoBoleto.RESERVADO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay boletos reservados; espera a que las reservas expiren o se confirmen.");
        }
        List<Premio> listaPremios = premios.findByRifaIdOrderByPosicionAsc(rifaId);
        if (listaPremios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La rifa no tiene premios registrados.");
        }
        List<Boleto> vendidos = boletos.findByRifaIdAndEstadoOrderByNumeroAsc(rifaId, EstadoBoleto.VENDIDO);
        if (vendidos.size() < listaPremios.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay suficientes boletos vendidos para asignar todos los premios.");
        }

        List<Boleto> elegidos;
        if (rifa.getMetodoSorteo() == MetodoSorteo.ALEATORIO) {
            elegidos = new ArrayList<>(vendidos);
            Collections.shuffle(elegidos, aleatorio);
            elegidos = new ArrayList<>(elegidos.subList(0, listaPremios.size()));
        } else {
            List<Integer> numeros = request == null ? null : request.numerosGanadores();
            if (numeros == null || numeros.size() != listaPremios.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para un sorteo de tipo LOTERIA o MANUAL debes indicar un número vendido por cada premio.");
            }
            if (numeros.stream().distinct().count() != numeros.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes asignar el mismo boleto a dos premios.");
            }
            Map<Integer, Boleto> porNumero = new LinkedHashMap<>();
            vendidos.forEach(b -> porNumero.put(b.getNumero(), b));
            elegidos = new ArrayList<>();
            for (Integer numero : numeros) {
                Boleto boleto = porNumero.get(numero);
                if (boleto == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El número " + numero + " no corresponde a un boleto vendido de esta rifa.");
                }
                elegidos.add(boleto);
            }
        }

        Instant ahora = Instant.now(clock);
        List<Ganador> nuevos = new ArrayList<>();
        List<Map<String, Object>> ganadoresCola = new ArrayList<>();
        List<SorteoDtos.GanadorDto> respuesta = new ArrayList<>();
        for (int i = 0; i < listaPremios.size(); i++) {
            Premio premio = listaPremios.get(i);
            Boleto boleto = elegidos.get(i);
            Ganador ganador = new Ganador();
            ganador.setRifaId(rifaId);
            ganador.setPremioId(premio.getId());
            ganador.setBoletoId(boleto.getId());
            ganador.setPublicado(true);
            ganador.setResultadoJson(null);
            ganador.setCreadoEn(ahora);
            nuevos.add(ganador);
            ganadoresCola.add(Map.of("premio", premio.getNombre(), "numero", boleto.getNumero()));
            respuesta.add(new SorteoDtos.GanadorDto(rifa.getCodigoPublico(), rifa.getTitulo(), premio.getPosicion(),
                premio.getNombre(), boleto.getNumero(), true, ahora));
        }
        ganadores.saveAll(nuevos);
        rifa.setEstado(EstadoRifa.SORTEADA);
        rifa.setActualizadaEn(ahora);
        rifas.save(rifa);

        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("rifa_codigo", rifa.getCodigoPublico());
        mensaje.put("titulo", rifa.getTitulo());
        mensaje.put("cantidad_boletos", rifa.getCantidadBoletos());
        mensaje.put("boletos_vendidos", vendidos.size());
        mensaje.put("ganadores", ganadoresCola);
        eventos.publishEvent(new SorteoProcesadoEvent(mensaje));
        return respuesta;
    }

    @Transactional(readOnly = true)
    public List<SorteoDtos.GanadorDto> consultarGanadores(String codigo) {
        Rifa rifa = rifas.findByCodigoPublico(codigo.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la rifa."));
        if (rifa.getEstado() != EstadoRifa.SORTEADA) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El sorteo de esta rifa aún no está publicado.");
        }
        List<SorteoDtos.GanadorDto> resultado = new ArrayList<>();
        for (Ganador ganador : ganadores.findByRifaIdOrderByCreadoEnAsc(rifa.getId())) {
            Premio premio = premios.findById(ganador.getPremioId()).orElseThrow();
            Boleto boleto = boletos.findById(ganador.getBoletoId()).orElseThrow();
            resultado.add(new SorteoDtos.GanadorDto(rifa.getCodigoPublico(), rifa.getTitulo(), premio.getPosicion(),
                premio.getNombre(), boleto.getNumero(), ganador.isPublicado(), ganador.getCreadoEn()));
        }
        return resultado;
    }

    private void exigirPropietario(Rifa rifa, UsuarioAutenticado actor) {
        if (actor.rol() != Rol.ADMIN && !(actor.rol() == Rol.ORGANIZADOR && actor.id().equals(rifa.getOrganizadorId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes realizar sorteos sobre esta rifa.");
        }
    }
}
