package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Ganador;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GanadorRepository extends JpaRepository<Ganador, Long> {
    boolean existsByRifaId(Long rifaId);
    List<Ganador> findByRifaIdOrderByCreadoEnAsc(Long rifaId);
}
