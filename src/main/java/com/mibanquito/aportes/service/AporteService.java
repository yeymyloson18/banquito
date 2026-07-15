package com.mibanquito.aportes.service;

import com.mibanquito.aportes.repository.AporteInicialRepository;
import com.mibanquito.aportes.repository.AporteMensualRepository;
import com.mibanquito.aportes.repository.MultaMoraRepository;
import com.mibanquito.aportes.repository.ParametroPeriodoRepository;
import com.mibanquito.aportes.repository.PeriodoAnualRefRepository;
import com.mibanquito.aportes.entity.AporteInicial;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Esqueleto (Fase 5): las firmas existen para que AporteServiceTest compile,
 * pero ningun metodo tiene logica real todavia (Principio III, test-first).
 * La implementacion real se escribe en la Fase 6, T033-T037.
 */
@Service
public class AporteService {

    private final AporteInicialRepository aporteInicialRepository;
    private final AporteMensualRepository aporteMensualRepository;
    private final MultaMoraRepository multaMoraRepository;
    private final ParametroPeriodoRepository parametroPeriodoRepository;
    private final PeriodoAnualRefRepository periodoAnualRefRepository;

    public AporteService(AporteInicialRepository aporteInicialRepository,
                          AporteMensualRepository aporteMensualRepository,
                          MultaMoraRepository multaMoraRepository,
                          ParametroPeriodoRepository parametroPeriodoRepository,
                          PeriodoAnualRefRepository periodoAnualRefRepository) {
        this.aporteInicialRepository = aporteInicialRepository;
        this.aporteMensualRepository = aporteMensualRepository;
        this.multaMoraRepository = multaMoraRepository;
        this.parametroPeriodoRepository = parametroPeriodoRepository;
        this.periodoAnualRefRepository = periodoAnualRefRepository;
    }

    public AporteInicial registrarAporteInicial(Long socioId, Long periodoId, BigDecimal monto, LocalDate fecha) {
        throw new UnsupportedOperationException("pendiente de implementar en Fase 6 (T033)");
    }

    public BigDecimal calcularDeudaAcumulada(Long socioId, Long periodoId, YearMonth mesHasta) {
        throw new UnsupportedOperationException("pendiente de implementar en Fase 6 (T034-T035)");
    }

    public void registrarAporteMensual(Long socioId, Long periodoId, YearMonth mesHasta,
                                        BigDecimal montoPagado, LocalDate fecha) {
        throw new UnsupportedOperationException("pendiente de implementar en Fase 6 (T036)");
    }

    public BigDecimal obtenerTotalMultasPeriodo(Long periodoId) {
        throw new UnsupportedOperationException("pendiente de implementar en Fase 6 (T037)");
    }
}
