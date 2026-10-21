package com.alexrifas.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByRol(Rol rol);
}
