package com.alexrifas.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.alexrifas.api.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca un token de forma atómica: devuelve 1 solo si seguía vigente. Así, dos peticiones
     * simultáneas con el mismo refresh token no pueden ambas tener éxito.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revocado = true where t.id = :id and t.revocado = false")
    int revocarUno(@Param("id") Long id);

    /** Cierra todas las sesiones de un usuario (se usa si se detecta reutilización de un token ya revocado). */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revocado = true where t.usuarioId = :usuarioId and t.revocado = false")
    int revocarTodos(@Param("usuarioId") Long usuarioId);
}
