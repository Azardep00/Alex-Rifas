package com.alexrifas.api.entity;

import com.alexrifas.api.model.EstadoRifa;
import com.alexrifas.api.model.MetodoSorteo;
import com.alexrifas.api.model.ReglaVacante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rifas")
public class Rifa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_publico", nullable = false, unique = true, length = 16)
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
    private String moneda = "COP";

    @Column(name = "cantidad_boletos", nullable = false)
    private int cantidadBoletos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRifa estado;

    @Column(name = "fecha_sorteo", nullable = false)
    private Instant fechaSorteo;

    @Column(name = "cierre_ventas_en", nullable = false)
    private Instant cierreVentasEn;

    @Column(name = "maximo_boletos_por_compra", nullable = false)
    private int maximoBoletosPorCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_sorteo", nullable = false, length = 20)
    private MetodoSorteo metodoSorteo;

    @Column(name = "loteria_nombre", length = 80)
    private String loteriaNombre;

    @Column(name = "numero_autorizacion", length = 60)
    private String numeroAutorizacion;

    @Column(name = "entidad_autorizadora", length = 120)
    private String entidadAutorizadora;

    /** Minutos que dura una reserva pendiente de pago (pago manual necesita más que 15). */
    @Column(name = "reserva_minutos", nullable = false)
    private int reservaMinutos = 15;

    /** Dígitos de la lotería que definen el ganador (2, 3 o 4). Nulo si la rifa no usa "últimos N dígitos". */
    @Column(name = "digitos")
    private Integer digitos;

    @Enumerated(EnumType.STRING)
    @Column(name = "regla_vacante", nullable = false, length = 30)
    private ReglaVacante reglaVacante = ReglaVacante.REPETIR_SIGUIENTE_LOTERIA;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "publicada_en")
    private Instant publicadaEn;

    @Version
    private Long version;

    public Long getId() { return id; }
    public String getCodigoPublico() { return codigoPublico; }
    public void setCodigoPublico(String codigoPublico) { this.codigoPublico = codigoPublico; }
    public Long getOrganizadorId() { return organizadorId; }
    public void setOrganizadorId(Long organizadorId) { this.organizadorId = organizadorId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public BigDecimal getPrecioBoleto() { return precioBoleto; }
    public void setPrecioBoleto(BigDecimal precioBoleto) { this.precioBoleto = precioBoleto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public int getCantidadBoletos() { return cantidadBoletos; }
    public void setCantidadBoletos(int cantidadBoletos) { this.cantidadBoletos = cantidadBoletos; }
    public EstadoRifa getEstado() { return estado; }
    public void setEstado(EstadoRifa estado) { this.estado = estado; }
    public Instant getFechaSorteo() { return fechaSorteo; }
    public void setFechaSorteo(Instant fechaSorteo) { this.fechaSorteo = fechaSorteo; }
    public Instant getCierreVentasEn() { return cierreVentasEn; }
    public void setCierreVentasEn(Instant cierreVentasEn) { this.cierreVentasEn = cierreVentasEn; }
    public int getMaximoBoletosPorCompra() { return maximoBoletosPorCompra; }
    public void setMaximoBoletosPorCompra(int maximoBoletosPorCompra) { this.maximoBoletosPorCompra = maximoBoletosPorCompra; }
    public MetodoSorteo getMetodoSorteo() { return metodoSorteo; }
    public void setMetodoSorteo(MetodoSorteo metodoSorteo) { this.metodoSorteo = metodoSorteo; }
    public String getLoteriaNombre() { return loteriaNombre; }
    public void setLoteriaNombre(String loteriaNombre) { this.loteriaNombre = loteriaNombre; }
    public String getNumeroAutorizacion() { return numeroAutorizacion; }
    public void setNumeroAutorizacion(String numeroAutorizacion) { this.numeroAutorizacion = numeroAutorizacion; }
    public String getEntidadAutorizadora() { return entidadAutorizadora; }
    public void setEntidadAutorizadora(String entidadAutorizadora) { this.entidadAutorizadora = entidadAutorizadora; }
    public int getReservaMinutos() { return reservaMinutos; }
    public void setReservaMinutos(int reservaMinutos) { this.reservaMinutos = reservaMinutos; }
    public Integer getDigitos() { return digitos; }
    public void setDigitos(Integer digitos) { this.digitos = digitos; }
    public ReglaVacante getReglaVacante() { return reglaVacante; }
    public void setReglaVacante(ReglaVacante reglaVacante) { this.reglaVacante = reglaVacante; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }
    public Instant getPublicadaEn() { return publicadaEn; }
    public void setPublicadaEn(Instant publicadaEn) { this.publicadaEn = publicadaEn; }
    public Long getVersion() { return version; }
}
