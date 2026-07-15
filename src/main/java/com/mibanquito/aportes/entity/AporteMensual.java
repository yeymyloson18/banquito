package com.mibanquito.aportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Aporte obligatorio de un socio correspondiente a un mes especifico de un
 * periodo. montoEsperado es historico e inmutable: se congela al momento de
 * generar el registro y nunca se recalcula si ParametroPeriodo cambia
 * despues (FR-008).
 */
@Entity
@Table(name = "aporte_mensual",
        uniqueConstraints = @UniqueConstraint(columnNames = {"socio_id", "periodo_id", "mes"}))
public class AporteMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "socio_id", nullable = false)
    private Long socioId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Convert(converter = YearMonthDateConverter.class)
    @Column(name = "mes", nullable = false)
    private YearMonth mes;

    @Column(name = "monto_esperado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoEsperado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoAporteMensual estado;

    protected AporteMensual() {
        // JPA
    }

    public AporteMensual(Long socioId, Long periodoId, YearMonth mes, BigDecimal montoEsperado) {
        this.socioId = Objects.requireNonNull(socioId, "socioId");
        this.periodoId = Objects.requireNonNull(periodoId, "periodoId");
        this.mes = Objects.requireNonNull(mes, "mes");
        this.montoEsperado = Objects.requireNonNull(montoEsperado, "montoEsperado");
        this.estado = EstadoAporteMensual.PENDIENTE;
    }

    public void marcarComoPagado(LocalDate fechaPago) {
        this.fechaPago = Objects.requireNonNull(fechaPago, "fechaPago");
        this.estado = EstadoAporteMensual.PAGADO;
    }

    public boolean estaPendiente() {
        return estado == EstadoAporteMensual.PENDIENTE;
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

    public YearMonth getMes() {
        return mes;
    }

    public BigDecimal getMontoEsperado() {
        return montoEsperado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public EstadoAporteMensual getEstado() {
        return estado;
    }
}
