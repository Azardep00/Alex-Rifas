package com.alexrifas.api.entity;

import com.alexrifas.api.model.EstadoResultado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/** Número que salió en un sorteo, cargado a mano y con evidencia. */
@Entity
@Table(name = "resultados_loteria")
public class ResultadoLoteria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loteria_id", nullable = false)
    private Long loteriaId;

    @Column(name = "fecha_sorteo", nullable = false)
    private LocalDate fechaSorteo;

    /** Cuatro dígitos como TEXTO: "0377" no puede convertirse en 377. */
    @Column(name = "numero_mayor", nullable = false, length = 4)
    private String numeroMayor;

    @Column(length = 3)
    private String serie;

    @Column(name = "numero_sorteo")
    private Integer numeroSorteo;

    @Column(nullable = false, length = 120)
    private String fuente;

    @Column(name = "evidencia_url", length = 500)
    private String evidenciaUrl;

    @Column(length = 500)
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoResultado estado = EstadoResultado.PENDIENTE_VERIFICACION;

    @Column(name = "cargado_por")
    private Long cargadoPor;

    @Column(name = "verificado_por")
    private Long verificadoPor;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    @Column(name = "anulado_por")
    private Long anuladoPor;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Version
    private long version;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Últimos {@code n} dígitos del número mayor, con ceros a la izquierda: 8954 con n=2 es "54"; 0377 con n=3
     * es "377". Es el número del boleto ganador de una rifa de {@code 10^n} boletos (ADR-06).
     */
    public String ultimosDigitos(int n) {
        if (n < 2 || n > 4) {
            throw new IllegalArgumentException("Solo se admiten los últimos 2, 3 o 4 dígitos.");
        }
        return numeroMayor.substring(numeroMayor.length() - n);
    }

    public Long getId() { return id; }
    public Long getLoteriaId() { return loteriaId; }
    public void setLoteriaId(Long loteriaId) { this.loteriaId = loteriaId; }
    public LocalDate getFechaSorteo() { return fechaSorteo; }
    public void setFechaSorteo(LocalDate fechaSorteo) { this.fechaSorteo = fechaSorteo; }
    public String getNumeroMayor() { return numeroMayor; }
    public void setNumeroMayor(String numeroMayor) { this.numeroMayor = numeroMayor; }
    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }
    public Integer getNumeroSorteo() { return numeroSorteo; }
    public void setNumeroSorteo(Integer numeroSorteo) { this.numeroSorteo = numeroSorteo; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public String getEvidenciaUrl() { return evidenciaUrl; }
    public void setEvidenciaUrl(String evidenciaUrl) { this.evidenciaUrl = evidenciaUrl; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public EstadoResultado getEstado() { return estado; }
    public void setEstado(EstadoResultado estado) { this.estado = estado; }
    public Long getCargadoPor() { return cargadoPor; }
    public void setCargadoPor(Long cargadoPor) { this.cargadoPor = cargadoPor; }
    public Long getVerificadoPor() { return verificadoPor; }
    public void setVerificadoPor(Long verificadoPor) { this.verificadoPor = verificadoPor; }
    public Instant getVerificadoEn() { return verificadoEn; }
    public void setVerificadoEn(Instant verificadoEn) { this.verificadoEn = verificadoEn; }
    public Long getAnuladoPor() { return anuladoPor; }
    public void setAnuladoPor(Long anuladoPor) { this.anuladoPor = anuladoPor; }
    public Instant getAnuladoEn() { return anuladoEn; }
    public void setAnuladoEn(Instant anuladoEn) { this.anuladoEn = anuladoEn; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public long getVersion() { return version; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
