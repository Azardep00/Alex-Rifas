package com.alexrifas.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alexrifas.api.service.ReservasPendientesExcedidasException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {
    @Test
    void elTopeDeReservasRespondeConEstado429MensajeClaroYRetryAfter() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/publico/reservas");

        ResponseEntity<ApiExceptionHandler.ErrorDto> respuesta = new ApiExceptionHandler().handleLimite(
            new ReservasPendientesExcedidasException("Ya tienes 3 reservas pendientes de pago.", 540), request);

        assertEquals(429, respuesta.getStatusCode().value());
        assertEquals("540", respuesta.getHeaders().getFirst("Retry-After"));
        assertEquals(429, respuesta.getBody().estado());
        assertTrue(respuesta.getBody().mensaje().contains("3 reservas pendientes"));
        assertEquals("/api/publico/reservas", respuesta.getBody().ruta());
    }
}
