package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Compra;
import com.alexrifas.api.model.EstadoCompra;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {
    interface EstadoConteo {
        EstadoCompra getEstado();
        long getTotal();
    }

    Optional<Compra> findByCodigo(String codigo);
    Optional<Compra> findByCodigoAndUsuarioId(String codigo, Long usuarioId);
    Optional<Compra> findByCodigoAndCompradorTelefono(String codigo, String compradorTelefono);
    List<Compra> findByUsuarioIdOrderByCreadaEnDesc(Long usuarioId);

    /** Reservas pendientes y vigentes de un teléfono, la más próxima a vencer primero (tope antiabuso). */
    List<Compra> findByCompradorTelefonoAndEstadoAndExpiraEnAfterOrderByExpiraEnAsc(
        String compradorTelefono, EstadoCompra estado, Instant ahora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.codigo = :codigo")
    Optional<Compra> findByCodigoForUpdate(@Param("codigo") String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Compra> findByEstadoAndExpiraEnLessThanEqual(EstadoCompra estado, Instant ahora);

    /** Suma de subtotales por estado; nula si no hay compras (el servicio la trata como cero). */
    @Query("select sum(c.subtotal) from Compra c where c.rifaId = :rifaId and c.estado = :estado")
    BigDecimal sumarSubtotal(@Param("rifaId") Long rifaId, @Param("estado") EstadoCompra estado);

    /** Dinero de reservas pendientes que siguen vigentes. */
    @Query("select sum(c.subtotal) from Compra c where c.rifaId = :rifaId "
        + "and c.estado = com.alexrifas.api.model.EstadoCompra.PENDIENTE and c.expiraEn > :ahora")
    BigDecimal sumarPendientesVigentes(@Param("rifaId") Long rifaId, @Param("ahora") Instant ahora);

    @Query("select c.estado as estado, count(c) as total from Compra c where c.rifaId = :rifaId group by c.estado")
    List<EstadoConteo> contarPorEstado(@Param("rifaId") Long rifaId);
}
