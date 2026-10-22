package com.alexrifas.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas @Scheduled (expiración de reservas vencidas en CompraService). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
