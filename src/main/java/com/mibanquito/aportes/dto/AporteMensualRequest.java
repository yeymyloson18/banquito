package com.mibanquito.aportes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Formulario de registro de aporte mensual (FR-002). mesHasta: el mes hasta
 * el cual se esta pagando (no un mes puntual, ver FR-012) — el sistema salda
 * en una sola operacion todos los meses pendientes con mes {@code <=
 * mesHasta}.
 */
public record AporteMensualRequest(

        @NotNull(message = "mesHasta es obligatorio")
        YearMonth mesHasta,

        @NotNull(message = "El monto pagado es obligatorio")
        @Positive(message = "El monto pagado debe ser mayor a 0")
        BigDecimal montoPagado,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha

) {
}
