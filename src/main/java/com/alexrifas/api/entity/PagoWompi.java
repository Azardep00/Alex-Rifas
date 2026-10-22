package com.alexrifas.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de pago. La confirmación actual es manual; no representa una integración con Wompi. */
@Entity
@Table(name = "pagos_wompi")
public class PagoWompi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "compra_id", nullable = false)
    private Long compraId;

    @Column(nullable = false, unique = true, length = 100)
    private String referencia;

    @Column(name = "transaccion_id", unique = true, length = 100)
    private String transaccionId;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "respuesta_json", columnDefinition = "jsonb")
    private Map<String, Object> respuestaJson;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public Long getId() { return id; }
    public Long getCompraId() { return compraId; }
    public void setCompraId(Long compraId) { this.compraId = compraId; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getTransaccionId() { return transaccionId; }
    public void setTransaccionId(String transaccionId) { this.transaccionId = transaccionId; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public Map<String, Object> getRespuestaJson() { return respuestaJson; }
    public void setRespuestaJson(Map<String, Object> respuestaJson) { this.respuestaJson = respuestaJson; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
