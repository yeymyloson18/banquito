package com.mibanquito.aportes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Formulario de registro de aporte inicial (FR-001). */
public record AporteInicialRequest(

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a 0")
        BigDecimal monto,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha

) {
}
