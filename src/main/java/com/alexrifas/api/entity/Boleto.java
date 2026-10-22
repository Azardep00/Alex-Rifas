package com.alexrifas.api.entity;

import com.alexrifas.api.model.EstadoBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "boletos")
public class Boleto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rifa_id", nullable = false)
    private Long rifaId;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoBoleto estado;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "reservado_hasta")
    private Instant reservadoHasta;

    public Long getId() { return id; }
    public Long getRifaId() { return rifaId; }
    public void setRifaId(Long rifaId) { this.rifaId = rifaId; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public EstadoBoleto getEstado() { return estado; }
    public void setEstado(EstadoBoleto estado) { this.estado = estado; }
    public Long getCompraId() { return compraId; }
    public void setCompraId(Long compraId) { this.compraId = compraId; }
    public Instant getReservadoHasta() { return reservadoHasta; }
    public void setReservadoHasta(Instant reservadoHasta) { this.reservadoHasta = reservadoHasta; }
}
