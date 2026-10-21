package com.alexrifas.api.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Tabla "rifas". El campo version evita que dos personas pisen los cambios del otro (bloqueo optimista). */
@Entity
@Table(name = "rifas")
public class Rifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_publico", nullable = false, length = 16)
    private String codigoPublico;

    @Column(name = "organizador_id", nullable = false)
    private Long organizadorId;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 4000)
    private String descripcion;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "precio_boleto", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioBoleto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(name = "cantidad_boletos", nullable = false)
    private int cantidadBoletos;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoRifa estado;

    @Column(name = "fecha_sorteo", nullable = false)
    private Instant fechaSorteo;

    @Column(name = "cierre_ventas_en", nullable = false)
    private Instant cierreVentasEn;

    @Column(name = "maximo_boletos_por_compra", nullable = false)
    private int maximoBoletosPorCompra;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "metodo_sorteo", nullable = false, length = 20)
    private MetodoSorteo metodoSorteo;

    @Column(name = "loteria_nombre", length = 80)
    private String loteriaNombre;

    @Column(name = "numero_autorizacion", length = 60)
    private String numeroAutorizacion;

    @Column(name = "entidad_autorizadora", length = 120)
    private String entidadAutorizadora;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "publicada_en")
    private Instant publicadaEn;

    @Version
    @Column(nullable = false)
    private long version;

    public Long getId() {
        return id;
    }

    public String getCodigoPublico() {
        return codigoPublico;
    }

    public void setCodigoPublico(String codigoPublico) {
        this.codigoPublico = codigoPublico;
    }

    public Long getOrganizadorId() {
        return organizadorId;
    }

    public void setOrganizadorId(Long organizadorId) {
        this.organizadorId = organizadorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public BigDecimal getPrecioBoleto() {
        return precioBoleto;
    }

    public void setPrecioBoleto(BigDecimal precioBoleto) {
        this.precioBoleto = precioBoleto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public int getCantidadBoletos() {
        return cantidadBoletos;
    }

    public void setCantidadBoletos(int cantidadBoletos) {
        this.cantidadBoletos = cantidadBoletos;
    }

    public EstadoRifa getEstado() {
        return estado;
    }

    public void setEstado(EstadoRifa estado) {
        this.estado = estado;
    }

    public Instant getFechaSorteo() {
        return fechaSorteo;
    }

    public void setFechaSorteo(Instant fechaSorteo) {
        this.fechaSorteo = fechaSorteo;
    }

    public Instant getCierreVentasEn() {
        return cierreVentasEn;
    }

    public void setCierreVentasEn(Instant cierreVentasEn) {
        this.cierreVentasEn = cierreVentasEn;
    }

    public int getMaximoBoletosPorCompra() {
        return maximoBoletosPorCompra;
    }

    public void setMaximoBoletosPorCompra(int maximoBoletosPorCompra) {
        this.maximoBoletosPorCompra = maximoBoletosPorCompra;
    }

    public MetodoSorteo getMetodoSorteo() {
        return metodoSorteo;
    }

    public void setMetodoSorteo(MetodoSorteo metodoSorteo) {
        this.metodoSorteo = metodoSorteo;
    }

    public String getLoteriaNombre() {
        return loteriaNombre;
    }

    public void setLoteriaNombre(String loteriaNombre) {
        this.loteriaNombre = loteriaNombre;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }

    public String getEntidadAutorizadora() {
        return entidadAutorizadora;
    }

    public void setEntidadAutorizadora(String entidadAutorizadora) {
        this.entidadAutorizadora = entidadAutorizadora;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(Instant creadaEn) {
        this.creadaEn = creadaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }

    public void setActualizadaEn(Instant actualizadaEn) {
        this.actualizadaEn = actualizadaEn;
    }

    public Instant getPublicadaEn() {
        return publicadaEn;
    }

    public void setPublicadaEn(Instant publicadaEn) {
        this.publicadaEn = publicadaEn;
    }

    public long getVersion() {
        return version;
    }
}
