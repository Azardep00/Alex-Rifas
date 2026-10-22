package com.alexrifas.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ganadores")
public class Ganador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rifa_id", nullable = false)
    private Long rifaId;

    @Column(name = "premio_id", nullable = false)
    private Long premioId;

    @Column(name = "boleto_id", nullable = false)
    private Long boletoId;

    @Column(nullable = false)
    private boolean publicado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado_json", columnDefinition = "jsonb")
    private String resultadoJson;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    public Long getId() { return id; }
    public Long getRifaId() { return rifaId; }
    public void setRifaId(Long rifaId) { this.rifaId = rifaId; }
    public Long getPremioId() { return premioId; }
    public void setPremioId(Long premioId) { this.premioId = premioId; }
    public Long getBoletoId() { return boletoId; }
    public void setBoletoId(Long boletoId) { this.boletoId = boletoId; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public String getResultadoJson() { return resultadoJson; }
    public void setResultadoJson(String resultadoJson) { this.resultadoJson = resultadoJson; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
