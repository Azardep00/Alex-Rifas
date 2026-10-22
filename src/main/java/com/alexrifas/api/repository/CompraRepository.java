package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.model.EstadoCompra;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraRepository extends JpaRepository<Compra, Long> {
    Optional<Compra> findByCodigo(String codigo);
    Optional<Compra> findByCodigoAndUsuarioId(String codigo, Long usuarioId);
    List<Compra> findByUsuarioIdOrderByCreadaEnDesc(Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.codigo = :codigo")
    Optional<Compra> findByCodigoForUpdate(@Param("codigo") String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Compra> findByEstadoAndExpiraEnLessThanEqual(EstadoCompra estado, Instant ahora);
}
