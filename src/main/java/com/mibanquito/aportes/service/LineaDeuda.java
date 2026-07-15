package com.mibanquito.aportes.service;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Linea individual del desglose de deuda acumulada (FR-005): un aporte
 * mensual o una multa, con su mes y monto. Usada para construir
 * DeudaAcumuladaDTO en la capa controller (Fase 8).
 */
public record LineaDeuda(YearMonth mes, String concepto, BigDecimal monto) {
}
