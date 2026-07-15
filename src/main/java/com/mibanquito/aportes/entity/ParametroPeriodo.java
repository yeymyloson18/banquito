package com.mibanquito.aportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Historial de configuracion vigente de aporte mensual y multa por periodo.
 * Ver data-model.md y research.md Sec.2.
 */
@Entity
@Table(name = "parametro_periodo")
public class ParametroPeriodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "monto_aporte_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAporteMensual;

    @Column(name = "monto_multa", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoMulta;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    protected ParametroPeriodo() {
        // JPA
    }

    public ParametroPeriodo(Long periodoId, BigDecimal montoAporteMensual, BigDecimal montoMulta,
                             LocalDate vigenteDesde) {
        this.periodoId = Objects.requireNonNull(periodoId, "periodoId");
        this.montoAporteMensual = Objects.requireNonNull(montoAporteMensual, "montoAporteMensual");
        this.montoMulta = Objects.requireNonNull(montoMulta, "montoMulta");
        this.vigenteDesde = Objects.requireNonNull(vigenteDesde, "vigenteDesde");
    }

    public Long getId() {
        return id;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public BigDecimal getMontoAporteMensual() {
        return montoAporteMensual;
    }

    public BigDecimal getMontoMulta() {
        return montoMulta;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }
}
