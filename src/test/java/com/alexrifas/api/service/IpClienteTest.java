package com.alexrifas.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IpClienteTest {
    @Test
    void sinProxyUsaLaDireccionDelSocketAunqueLleguePorCabecera() {
        assertEquals("10.0.0.5", IpCliente.resolver("1.2.3.4", "10.0.0.5", 0));
    }

    @Test
    void conUnProxyTomaLaUltimaEntradaYNoLaQueEscribioElCliente() {
        // El cliente envió "6.6.6.6"; el proxy añadió la IP real "198.51.100.7".
        assertEquals("198.51.100.7", IpCliente.resolver("6.6.6.6, 198.51.100.7", "10.0.0.5", 1));
    }

    @Test
    void conDosProxiesTomaLaPenultima() {
        assertEquals("198.51.100.7", IpCliente.resolver("6.6.6.6, 198.51.100.7, 10.1.1.1", "10.0.0.5", 2));
    }

    @Test
    void siHayMenosEntradasQueSaltosUsaLaPrimera() {
        assertEquals("198.51.100.7", IpCliente.resolver("198.51.100.7", "10.0.0.5", 3));
    }

    @Test
    void cabeceraAusenteOBasuraCaeAlSocket() {
        assertEquals("10.0.0.5", IpCliente.resolver(null, "10.0.0.5", 1));
        assertEquals("10.0.0.5", IpCliente.resolver("  ", "10.0.0.5", 1));
        assertEquals("10.0.0.5", IpCliente.resolver("no-es-ip; DROP", "10.0.0.5", 1));
    }

    @Test
    void aceptaIpv6EnMinusculas() {
        assertEquals("2001:db8::1", IpCliente.resolver("2001:DB8::1", "10.0.0.5", 1));
    }
}
