package com.alexrifas.api.repository;

import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.EstadoResultado;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoLoteriaRepository extends JpaRepository<ResultadoLoteria, Long> {
    /** ¿Hay ya un resultado vigente (no anulado) para ese sorteo? */
    boolean existsByLoteriaIdAndFechaSorteoAndEstadoNot(Long loteriaId, LocalDate fechaSorteo, EstadoResultado estado);

    Page<ResultadoLoteria> findByLoteriaId(Long loteriaId, Pageable pageable);

    Page<ResultadoLoteria> findByLoteriaIdAndEstado(Long loteriaId, EstadoResultado estado, Pageable pageable);
}
