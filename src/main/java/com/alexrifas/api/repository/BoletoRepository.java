package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.model.EstadoBoleto;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoletoRepository extends JpaRepository<Boleto, Long> {
    long countByRifaIdAndEstado(Long rifaId, EstadoBoleto estado);
    long countByRifaId(Long rifaId);
    void deleteByRifaId(Long rifaId);
    List<Boleto> findByCompraId(Long compraId);
    List<Boleto> findByRifaIdAndEstadoOrderByNumeroAsc(Long rifaId, EstadoBoleto estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Boleto b where b.rifaId = :rifaId and b.numero in :numeros order by b.numero")
    List<Boleto> findByRifaIdAndNumeroInForUpdate(@Param("rifaId") Long rifaId,
                                                  @Param("numeros") Collection<Integer> numeros);
}
