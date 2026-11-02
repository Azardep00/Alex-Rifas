package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Ganador;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GanadorRepository extends JpaRepository<Ganador, Long> {
    boolean existsByRifaId(Long rifaId);
    /** ¿Algún premio fue adjudicado con este resultado de lotería? (si sí, ya no se puede anular). */
    boolean existsByResultadoLoteriaId(Long resultadoLoteriaId);
    List<Ganador> findByRifaIdOrderByCreadoEnAsc(Long rifaId);
}
