package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.model.EstadoBoleto;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoletoRepository extends JpaRepository<Boleto, Long> {
    /** Solo los boletos no disponibles: la grilla se arma rellenando el resto como libres. */
    interface OcupadoView {
        int getNumero();
        EstadoBoleto getEstado();
        Instant getReservadoHasta();
    }

    @Query("select b.numero as numero, b.estado as estado, b.reservadoHasta as reservadoHasta "
        + "from Boleto b where b.rifaId = :rifaId and b.estado <> com.alexrifas.api.model.EstadoBoleto.DISPONIBLE")
    List<OcupadoView> findOcupadosByRifaId(@Param("rifaId") Long rifaId);

    long countByRifaIdAndEstado(Long rifaId, EstadoBoleto estado);
    long countByRifaId(Long rifaId);
    void deleteByRifaId(Long rifaId);
    List<Boleto> findByCompraId(Long compraId);
    List<Boleto> findByCompraIdIn(Collection<Long> compraIds);
    /** Reservas vigentes (las vencidas que el job aún no procesó no cuentan). */
    long countByRifaIdAndEstadoAndReservadoHastaAfter(Long rifaId, EstadoBoleto estado, Instant ahora);
    List<Boleto> findByRifaIdAndEstadoOrderByNumeroAsc(Long rifaId, EstadoBoleto estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Boleto b where b.rifaId = :rifaId and b.numero in :numeros order by b.numero")
    List<Boleto> findByRifaIdAndNumeroInForUpdate(@Param("rifaId") Long rifaId,
                                                  @Param("numeros") Collection<Integer> numeros);
}
