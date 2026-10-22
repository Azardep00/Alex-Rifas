package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Premio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremioRepository extends JpaRepository<Premio, Long> {
    List<Premio> findByRifaIdOrderByPosicionAsc(Long rifaId);
    boolean existsByRifaIdAndPosicion(Long rifaId, int posicion);
    long countByRifaId(Long rifaId);
}
