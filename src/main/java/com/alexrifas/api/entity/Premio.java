package com.alexrifas.api.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla "premios". La posición 1 es el premio mayor. */
@Entity
@Table(name = "premios")
public class Premio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rifa_id", nullable = false)
    private Long rifaId;

    @Column(nullable = false)
    private int posicion;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "valor_estimado", precision = 14, scale = 2)
    private BigDecimal valorEstimado;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    public Long getId() {
        return id;
    }

    public Long getRifaId() {
        return rifaId;
    }

    public void setRifaId(Long rifaId) {
        this.rifaId = rifaId;
    }

    public int getPosicion() {
        return posicion;
    }

    public void setPosicion(int posicion) {
        this.posicion = posicion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public void setValorEstimado(BigDecimal valorEstimado) {
        this.valorEstimado = valorEstimado;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }
}
