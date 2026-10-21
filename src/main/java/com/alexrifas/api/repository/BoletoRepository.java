package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.model.EstadoBoleto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoletoRepository extends JpaRepository<Boleto, Long> {
    long countByRifaIdAndEstado(Long rifaId, EstadoBoleto estado);
    long countByRifaId(Long rifaId);
    void deleteByRifaId(Long rifaId);
}
