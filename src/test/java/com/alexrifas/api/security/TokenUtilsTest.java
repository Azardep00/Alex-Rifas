package com.alexrifas.api.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TokenUtilsTest {
    @Test
    void mismoTokenProduceMismoHashHexadecimalDe64Caracteres() {
        String hash = TokenUtils.sha256("refresh-token-de-prueba");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, TokenUtils.sha256("refresh-token-de-prueba"));
    }

    @Test
    void elHashNoEsElTokenOriginal() {
        String token = "token-en-claro-que-no-debe-guardarse";
        assertNotEquals(token, TokenUtils.sha256(token));
    }
}
