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
 * Aporte de capital unico registrado al ingreso de un socio a un periodo.
 * Su fecha determina el mes de ingreso del socio al periodo (FR-013).
 */
@Entity
@Table(name = "aporte_inicial")
public class AporteInicial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "socio_id", nullable = false)
    private Long socioId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    protected AporteInicial() {
        // JPA
    }

    public AporteInicial(Long socioId, Long periodoId, BigDecimal monto, LocalDate fecha) {
        this.socioId = Objects.requireNonNull(socioId, "socioId");
        this.periodoId = Objects.requireNonNull(periodoId, "periodoId");
        this.monto = Objects.requireNonNull(monto, "monto");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
    }

    public Long getId() {
        return id;
    }

    public Long getSocioId() {
        return socioId;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
