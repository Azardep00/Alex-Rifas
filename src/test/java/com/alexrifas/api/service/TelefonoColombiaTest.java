package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TelefonoColombiaTest {
    @Test
    void normalizaFormatosComunes() {
        assertEquals("+573001234567", TelefonoColombia.normalizar("300 123 4567"));
        assertEquals("+573001234567", TelefonoColombia.normalizar("+57 300-123-4567"));
        assertEquals("+573001234567", TelefonoColombia.normalizar("573001234567"));
        assertEquals("+573001234567", TelefonoColombia.normalizar("(300) 123.4567"));
    }

    @Test
    void rechazaNumerosQueNoSonCelularColombiano() {
        for (String malo : new String[] {null, "", "12345", "6012345678", "30012345678", "abc", "3333333333", "+57 333 333 3333"}) {
            assertThrows(ResponseStatusException.class, () -> TelefonoColombia.normalizar(malo));
        }
    }
}
