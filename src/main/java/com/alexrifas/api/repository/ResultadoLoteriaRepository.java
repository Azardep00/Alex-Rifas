package com.alexrifas.api.repository;

import com.alexrifas.api.entity.ResultadoLoteria;
import com.alexrifas.api.model.EstadoResultado;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultadoLoteriaRepository extends JpaRepository<ResultadoLoteria, Long> {
    /** ¿Hay ya un resultado vigente (no anulado) para ese sorteo? */
    boolean existsByLoteriaIdAndFechaSorteoAndEstadoNot(Long loteriaId, LocalDate fechaSorteo, EstadoResultado estado);

    Page<ResultadoLoteria> findByLoteriaId(Long loteriaId, Pageable pageable);

    Page<ResultadoLoteria> findByLoteriaIdAndEstado(Long loteriaId, EstadoResultado estado, Pageable pageable);

    /**
     * Resultados no anulados de una lotería desde {@code desde} (inclusive), del más antiguo al más reciente.
     * Incluye los PENDIENTE_VERIFICACION a propósito: el sorteo debe detenerse ante uno, no saltárselo.
     */
    @Query("select r from ResultadoLoteria r where r.loteriaId = :loteriaId "
        + "and r.estado <> com.alexrifas.api.model.EstadoResultado.ANULADO and r.fechaSorteo >= :desde "
        + "order by r.fechaSorteo asc")
    List<ResultadoLoteria> findVigentesDesde(@Param("loteriaId") Long loteriaId, @Param("desde") LocalDate desde);

    /**
     * Igual que {@link #findVigentesDesde} pero bloqueando las filas: el sorteo las lee y nadie puede verificarlas
     * o anularlas hasta que termine su transacción. Se usa solo al EJECUTAR el sorteo, no al previsualizar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResultadoLoteria r where r.loteriaId = :loteriaId "
        + "and r.estado <> com.alexrifas.api.model.EstadoResultado.ANULADO and r.fechaSorteo >= :desde "
        + "order by r.fechaSorteo asc")
    List<ResultadoLoteria> findVigentesDesdeParaActualizar(@Param("loteriaId") Long loteriaId,
                                                           @Param("desde") LocalDate desde);

    /** Para anular: bloquea la fila y evita que un sorteo en curso use un resultado mientras se anula. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResultadoLoteria r where r.id = :id")
    Optional<ResultadoLoteria> findByIdParaActualizar(@Param("id") Long id);
}
