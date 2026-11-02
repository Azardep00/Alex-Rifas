package com.alexrifas.api.repository;

import com.alexrifas.api.entity.Loteria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteriaRepository extends JpaRepository<Loteria, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    List<Loteria> findAllByOrderByNombreAsc();
    List<Loteria> findByActivaOrderByNombreAsc(boolean activa);
}
