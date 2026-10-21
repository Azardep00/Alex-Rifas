package com.alexrifas.api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.alexrifas.api.entity.Rifa;
import com.alexrifas.api.model.EstadoRifa;

public interface RifaRepository extends JpaRepository<Rifa, Long> {

    Optional<Rifa> findByCodigoPublico(String codigoPublico);

    boolean existsByCodigoPublico(String codigoPublico);

    Page<Rifa> findByEstadoOrderByFechaSorteoAsc(EstadoRifa estado, Pageable pageable);

    Page<Rifa> findByOrganizadorIdOrderByCreadaEnDesc(Long organizadorId, Pageable pageable);
}
