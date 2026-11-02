package com.alexrifas.api.entity;

import com.alexrifas.api.model.TipoLoteria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;

/** Un sorteo contra el que se puede jugar una rifa (una lotería tradicional o una franja de chance). */
@Entity
@Table(name = "loterias")
public class Loteria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoLoteria tipo = TipoLoteria.LOTERIA;

    /** Días de referencia en ISO separados por coma ("4" = jueves, "1,2,3,4,5,6,7" = a diario). Nulo = sin definir. */
    @Column(name = "dias_semana", length = 20)
    private String diasSemana;

    @Column(name = "hora_sorteo")
    private LocalTime horaSorteo;

    @Column(name = "sitio_oficial_url", length = 300)
    private String sitioOficialUrl;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public TipoLoteria getTipo() { return tipo; }
    public void setTipo(TipoLoteria tipo) { this.tipo = tipo; }
    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }
    public LocalTime getHoraSorteo() { return horaSorteo; }
    public void setHoraSorteo(LocalTime horaSorteo) { this.horaSorteo = horaSorteo; }
    public String getSitioOficialUrl() { return sitioOficialUrl; }
    public void setSitioOficialUrl(String sitioOficialUrl) { this.sitioOficialUrl = sitioOficialUrl; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
