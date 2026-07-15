package com.mibanquito.aportes.service;

import com.mibanquito.aportes.entity.AporteInicial;
import com.mibanquito.aportes.entity.AporteMensual;
import com.mibanquito.aportes.entity.MultaMora;
import com.mibanquito.aportes.entity.ParametroPeriodo;
import com.mibanquito.aportes.entity.PeriodoAnualRef;
import com.mibanquito.aportes.repository.AporteInicialRepository;
import com.mibanquito.aportes.repository.AporteMensualRepository;
import com.mibanquito.aportes.repository.MultaMoraRepository;
import com.mibanquito.aportes.repository.ParametroPeriodoRepository;
import com.mibanquito.aportes.repository.PeriodoAnualRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de Aportes: FR-001 a FR-014 de specs/001-aportes/spec.md.
 * Ver research.md Sec.7-11 y data-model.md para el diseno de
 * resolverMesesHasta (generacion perezosa) y el pago consolidado multi-mes.
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

    /** FR-001, FR-011. */
    @Transactional
    public AporteInicial registrarAporteInicial(Long socioId, Long periodoId, BigDecimal monto, LocalDate fecha) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del aporte inicial debe ser mayor a 0");
        }
        rechazarSiPeriodoCerrado(periodoId);
        return aporteInicialRepository.save(new AporteInicial(socioId, periodoId, monto, fecha));
    }

    /** FR-004, FR-007, FR-008, FR-013. */
    public BigDecimal calcularDeudaAcumulada(Long socioId, Long periodoId, YearMonth mesHasta) {
        List<MesResuelto> meses = resolverMesesHasta(socioId, periodoId, mesHasta);
        return sumarDeuda(meses);
    }

    /** FR-002, FR-009, FR-010, FR-011, FR-012. */
    @Transactional
    public void registrarAporteMensual(Long socioId, Long periodoId, YearMonth mesHasta,
                                        BigDecimal montoPagado, LocalDate fecha) {
        rechazarSiPeriodoCerrado(periodoId);

        aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(socioId, periodoId, mesHasta)
                .filter(am -> !am.estaPendiente())
                .ifPresent(am -> {
                    throw new AporteMensualYaPagadoException(
                            "El mes " + mesHasta + " ya fue registrado como pagado para el socio " + socioId);
                });

        List<MesResuelto> meses = resolverMesesHasta(socioId, periodoId, mesHasta);
        BigDecimal deudaAcumulada = sumarDeuda(meses);

        if (montoPagado == null || montoPagado.compareTo(deudaAcumulada) != 0) {
            throw new PagoParcialNoPermitidoException(
                    "El monto pagado debe coincidir exactamente con la deuda acumulada de S/ " + deudaAcumulada);
        }

        for (MesResuelto mesResuelto : meses) {
            if (mesResuelto.aporteMensual().estaPendiente()) {
                mesResuelto.aporteMensual().marcarComoPagado(fecha);
                aporteMensualRepository.save(mesResuelto.aporteMensual());
            }
        }
    }

    /** FR-006: expuesto para que el futuro modulo Caja sume las multas a la caja del banco. */
    public BigDecimal obtenerTotalMultasPeriodo(Long periodoId) {
        return multaMoraRepository.sumMontoByPeriodoId(periodoId);
    }

    /**
     * Desglose de la deuda acumulada por concepto (FR-005), usado por el
     * controller para construir DeudaAcumuladaDTO (GET /deuda, FR-004).
     */
    public List<LineaDeuda> obtenerDesgloseDeuda(Long socioId, Long periodoId, YearMonth mesHasta) {
        List<LineaDeuda> lineas = new ArrayList<>();
        for (MesResuelto mesResuelto : resolverMesesHasta(socioId, periodoId, mesHasta)) {
            if (!mesResuelto.aporteMensual().estaPendiente()) {
                continue;
            }
            YearMonth mes = mesResuelto.aporteMensual().getMes();
            lineas.add(new LineaDeuda(mes, "Aporte " + mes, mesResuelto.aporteMensual().getMontoEsperado()));
            if (mesResuelto.multaMora() != null) {
                lineas.add(new LineaDeuda(mes, "Multa " + mes, mesResuelto.multaMora().getMonto()));
            }
        }
        return lineas;
    }

    // ---------------------------------------------------------------

    private BigDecimal sumarDeuda(List<MesResuelto> meses) {
        BigDecimal total = BigDecimal.ZERO;
        for (MesResuelto mesResuelto : meses) {
            if (mesResuelto.aporteMensual().estaPendiente()) {
                total = total.add(mesResuelto.aporteMensual().getMontoEsperado());
                if (mesResuelto.multaMora() != null) {
                    total = total.add(mesResuelto.multaMora().getMonto());
                }
            }
        }
        return total;
    }

    /**
     * Genera de forma perezosa los AporteMensual PENDIENTE faltantes entre el
     * mes de ingreso del socio (derivado de AporteInicial.fecha, FR-013) y
     * mesHasta, congelando montoEsperado/MultaMora.monto desde el
     * ParametroPeriodo vigente en cada mes (FR-007, FR-008). Los meses que ya
     * existian NUNCA se recalculan (FR-008): se devuelven tal cual estaban.
     */
    private List<MesResuelto> resolverMesesHasta(Long socioId, Long periodoId, YearMonth mesHasta) {
        YearMonth mesIngreso = obtenerMesIngreso(socioId, periodoId);
        List<MesResuelto> resultado = new ArrayList<>();

        for (YearMonth mes = mesIngreso; !mes.isAfter(mesHasta); mes = mes.plusMonths(1)) {
            Optional<AporteMensual> existente =
                    aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(socioId, periodoId, mes);
            if (existente.isPresent()) {
                resultado.add(new MesResuelto(existente.get(), null));
                continue;
            }

            boolean vencido = mes.isBefore(mesHasta);
            ParametroPeriodo parametro = resolverParametroVigente(periodoId, mes.atEndOfMonth());
            AporteMensual nuevo = aporteMensualRepository.save(
                    new AporteMensual(socioId, periodoId, mes, parametro.getMontoAporteMensual()));

            MultaMora multa = null;
            if (vencido) {
                multa = multaMoraRepository.save(new MultaMora(nuevo, parametro.getMontoMulta(), mes));
            }
            resultado.add(new MesResuelto(nuevo, multa));
        }
        return resultado;
    }

    private YearMonth obtenerMesIngreso(Long socioId, Long periodoId) {
        return aporteInicialRepository.findFirstBySocioIdAndPeriodoIdOrderByFechaAsc(socioId, periodoId)
                .map(ai -> YearMonth.from(ai.getFecha()))
                .orElseThrow(() -> new IllegalStateException(
                        "El socio " + socioId + " no tiene un aporte inicial registrado en el periodo " + periodoId));
    }

    private ParametroPeriodo resolverParametroVigente(Long periodoId, LocalDate fecha) {
        return parametroPeriodoRepository
                .findTopByPeriodoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(periodoId, fecha)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay ParametroPeriodo vigente para el periodo " + periodoId + " en " + fecha));
    }

    private void rechazarSiPeriodoCerrado(Long periodoId) {
        Optional<PeriodoAnualRef> periodo = periodoAnualRefRepository.findById(periodoId);
        if (periodo.isPresent() && periodo.get().estaCerrado()) {
            throw new PeriodoCerradoException(
                    "El periodo " + periodoId + " esta cerrado; no se admiten aportes");
        }
    }

    private record MesResuelto(AporteMensual aporteMensual, MultaMora multaMora) {
    }
}
