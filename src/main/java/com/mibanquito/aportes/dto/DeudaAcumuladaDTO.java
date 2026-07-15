package com.mibanquito.aportes.dto;

import com.mibanquito.aportes.service.LineaDeuda;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de consulta de deuda acumulada (FR-004, SC-004): desglose por
 * concepto (aporte de que mes, multa de que mes — FR-005) y el total exacto.
 */
public record DeudaAcumuladaDTO(

        List<LineaDeuda> desglose,

        BigDecimal total

) {
}
