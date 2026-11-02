package com.alexrifas.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code auditoria_eventos} (tabla de V1). La columna {@code ip_origen} no se mapea a propósito:
 * el {@code request_id} ya permite cruzar con el log de acceso, y así no dependemos del tipo INET.
 */
@Entity
@Table(name = "auditoria_eventos")
public class AuditoriaEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 80)
    private String entidad;

    @Column(name = "entidad_id", length = 80)
    private String entidadId;

    @Column(nullable = false, length = 80)
    private String accion;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalles_json", columnDefinition = "jsonb")
    private Map<String, Object> detalles;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }
    public String getEntidadId() { return entidadId; }
    public void setEntidadId(String entidadId) { this.entidadId = entidadId; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Map<String, Object> getDetalles() { return detalles; }
    public void setDetalles(Map<String, Object> detalles) { this.detalles = detalles; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
