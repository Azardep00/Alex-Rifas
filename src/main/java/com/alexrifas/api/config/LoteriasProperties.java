package com.alexrifas.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reglas de carga de resultados de lotería. Variable de entorno: {@code LOTERIAS_EXIGIR_SEGUNDO_VERIFICADOR}.
 *
 * @param exigirSegundoVerificador si es {@code true}, quien carga un resultado no puede verificarlo: lo debe
 *                                 verificar otro administrador. Por defecto {@code false} porque el despliegue
 *                                 inicial tiene un solo administrador; actívalo cuando haya dos o más. Aun
 *                                 desactivado, cargar y verificar son dos acciones separadas y auditadas.
 */
@ConfigurationProperties(prefix = "app.loterias")
public record LoteriasProperties(@DefaultValue("false") boolean exigirSegundoVerificador) {
}
