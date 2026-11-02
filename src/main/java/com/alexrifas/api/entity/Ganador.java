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

    /** Resultado de lotería verificado que decidió este premio (nulo en sorteos ALEATORIO/MANUAL). */
    @Column(name = "resultado_loteria_id")
    private Long resultadoLoteriaId;

    /** Número que salió de la lotería (últimos N dígitos); difiere del boleto si se adjudicó por cupo vacío. */
    @Column(name = "numero_resultado")
    private Integer numeroResultado;

    @Column(name = "adjudicado_por_vacante", nullable = false)
    private boolean adjudicadoPorVacante;

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
    public Long getResultadoLoteriaId() { return resultadoLoteriaId; }
    public void setResultadoLoteriaId(Long resultadoLoteriaId) { this.resultadoLoteriaId = resultadoLoteriaId; }
    public Integer getNumeroResultado() { return numeroResultado; }
    public void setNumeroResultado(Integer numeroResultado) { this.numeroResultado = numeroResultado; }
    public boolean isAdjudicadoPorVacante() { return adjudicadoPorVacante; }
    public void setAdjudicadoPorVacante(boolean adjudicadoPorVacante) { this.adjudicadoPorVacante = adjudicadoPorVacante; }
}
