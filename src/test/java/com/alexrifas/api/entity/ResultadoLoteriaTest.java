package com.alexrifas.api.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResultadoLoteriaTest {
    private static ResultadoLoteria con(String numeroMayor) {
        ResultadoLoteria r = new ResultadoLoteria();
        r.setNumeroMayor(numeroMayor);
        return r;
    }

    @Test
    void elEjemploDelNegocio8954GanaEl54EnCienEl954EnMilYEl8954EnDiezMil() {
        ResultadoLoteria r = con("8954");
        assertEquals("54", r.ultimosDigitos(2));
        assertEquals("954", r.ultimosDigitos(3));
        assertEquals("8954", r.ultimosDigitos(4));
    }

    @Test
    void conservaLosCerosALaIzquierda() {
        ResultadoLoteria r = con("0377");
        assertEquals("77", r.ultimosDigitos(2));
        assertEquals("377", r.ultimosDigitos(3));
        assertEquals("0377", r.ultimosDigitos(4));

        ResultadoLoteria ceros = con("0007");
        assertEquals("07", ceros.ultimosDigitos(2));
        assertEquals("007", ceros.ultimosDigitos(3));
    }

    @Test
    void soloAdmiteDosTresOCuatroDigitos() {
        ResultadoLoteria r = con("8954");
        assertThrows(IllegalArgumentException.class, () -> r.ultimosDigitos(1));
        assertThrows(IllegalArgumentException.class, () -> r.ultimosDigitos(5));
    }
}
