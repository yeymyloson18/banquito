package com.mibanquito.aportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Penalidad generada por un AporteMensual pagado fuera de plazo. monto es
 * historico e inmutable: snapshot del ParametroPeriodo.montoMulta vigente al
 * momento en que el mes vencio (FR-008).
 */
@Entity
@Table(name = "multa_mora")
public class MultaMora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "aporte_mensual_id", nullable = false, unique = true)
    private AporteMensual aporteMensual;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Convert(converter = YearMonthDateConverter.class)
    @Column(name = "mes_generada", nullable = false)
    private YearMonth mesGenerada;

    protected MultaMora() {
        // JPA
    }

    public MultaMora(AporteMensual aporteMensual, BigDecimal monto, YearMonth mesGenerada) {
        this.aporteMensual = Objects.requireNonNull(aporteMensual, "aporteMensual");
        this.monto = Objects.requireNonNull(monto, "monto");
        this.mesGenerada = Objects.requireNonNull(mesGenerada, "mesGenerada");
    }

    public Long getId() {
        return id;
    }

    public AporteMensual getAporteMensual() {
        return aporteMensual;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public YearMonth getMesGenerada() {
        return mesGenerada;
    }
}
