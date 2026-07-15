package com.mibanquito.aportes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Referencia de solo lectura a la tabla periodo_anual (propiedad real del
 * futuro modulo Apertura de periodo anual). Aportes solo necesita conocer
 * el estado ABIERTO/CERRADO para FR-011; no gestiona el ciclo de vida del
 * periodo (docs/arquitectura-general.md: un modulo puede referenciar
 * entidades de otro modulo por su tipo sin duplicar su logica de negocio).
 */
@Entity
@Table(name = "periodo_anual")
public class PeriodoAnualRef {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoPeriodo estado;

    protected PeriodoAnualRef() {
        // JPA
    }

    public PeriodoAnualRef(Long id, EstadoPeriodo estado) {
        this.id = id;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public EstadoPeriodo getEstado() {
        return estado;
    }

    public boolean estaCerrado() {
        return estado == EstadoPeriodo.CERRADO;
    }
}
