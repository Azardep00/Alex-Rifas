package com.alexrifas.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alexrifas.api.entity.Premio;

public interface PremioRepository extends JpaRepository<Premio, Long> {

    List<Premio> findByRifaIdOrderByPosicionAsc(Long rifaId);

    Optional<Premio> findByIdAndRifaId(Long id, Long rifaId);

    boolean existsByRifaIdAndPosicion(Long rifaId, int posicion);

    long countByRifaId(Long rifaId);
}
