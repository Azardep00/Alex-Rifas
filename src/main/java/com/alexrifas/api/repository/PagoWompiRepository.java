package com.alexrifas.api.repository;

import com.alexrifas.api.entity.PagoWompi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoWompiRepository extends JpaRepository<PagoWompi, Long> {
    boolean existsByCompraIdAndEstado(Long compraId, String estado);
}
