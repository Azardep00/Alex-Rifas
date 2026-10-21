package com.alexrifas.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alexrifas.api.entity.Boleto;
import com.alexrifas.api.model.EstadoBoleto;

public interface BoletoRepository extends JpaRepository<Boleto, Long> {

    long countByRifaIdAndEstado(Long rifaId, EstadoBoleto estado);

    long countByRifaId(Long rifaId);
}
