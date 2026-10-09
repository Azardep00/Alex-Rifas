package com.alexrifas.api.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    /**
     * Reloj inyectable: toda la lógica con fechas (reservas, expiración, sorteo) usa este bean,
     * así los tests pueden avanzar el tiempo sin esperar minutos reales.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * BCrypt con sal aleatoria por contraseña. (El proyecto de Tamales usaba SHA-256 sin sal,
     * que es rápido de romper por fuerza bruta y vulnerable a tablas arcoíris.)
     */
    @Bean
    public PasswordEncoder passwordEncoder(AppProperties props) {
        return new BCryptPasswordEncoder(props.seguridad().bcryptCosto());
    }

    /** Activa las tareas programadas solo si app.scheduler.habilitado=true (apagado en los tests). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduler.habilitado", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
