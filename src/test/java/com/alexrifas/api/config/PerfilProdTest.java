package com.alexrifas.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guarda el perfil de producción (CP-20). No levanta Spring: lee {@code application-prod.yml} tal cual y falla si
 * alguien reintroduce un valor por omisión en un secreto (la API debe NO arrancar si falta) o reactiva Swagger.
 */
class PerfilProdTest {

    private static Map<String, Object> prod;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void cargar() throws Exception {
        try (InputStream in = PerfilProdTest.class.getResourceAsStream("/application-prod.yml")) {
            assertNotNull(in, "application-prod.yml debe existir en el classpath");
            prod = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object en(String ruta) {
        Object actual = prod;
        for (String clave : ruta.split("\\.")) {
            assertTrue(actual instanceof Map, "No existe la ruta " + ruta + " (falla en «" + clave + "»)");
            actual = ((Map<String, Object>) actual).get(clave);
        }
        return actual;
    }

    @Test
    void losSecretosSoloVienenDelEntornoYSinValorPorOmision() {
        // Debe ser exactamente ${VARIABLE}: con «:valor» la API arrancaría con un secreto conocido.
        assertEquals("${DB_URL}", en("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", en("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", en("spring.datasource.password"));
        assertEquals("${JWT_SECRET}", en("app.seguridad.jwt-secret"));
    }

    @Test
    void swaggerYApiDocsEstanApagados() {
        assertEquals(Boolean.FALSE, en("springdoc.api-docs.enabled"));
        assertEquals(Boolean.FALSE, en("springdoc.swagger-ui.enabled"));
    }

    @Test
    void soloSeExponeElHealthCheck() {
        assertEquals("health", en("management.endpoints.web.exposure.include"));
    }

    @Test
    void losErroresNoFiltranDetallesInternos() {
        assertEquals("never", en("server.error.include-stacktrace"));
        assertEquals("never", en("server.error.include-message"));
    }

    @Test
    void redisEsOpcionalYNoTumbaElHealthCheckPorOmision() {
        Object habilitado = en("management.health.redis.enabled");
        assertEquals("${HEALTH_REDIS_ENABLED:false}", habilitado);
    }

    @Test
    void elPuertoLoDefineElHost() {
        assertEquals("${PORT:8080}", en("server.port"));
    }
}
