package com.mibanquito.aportes.service;

import com.mibanquito.aportes.entity.AporteInicial;
import com.mibanquito.aportes.entity.AporteMensual;
import com.mibanquito.aportes.entity.EstadoAporteMensual;
import com.mibanquito.aportes.entity.EstadoPeriodo;
import com.mibanquito.aportes.entity.MultaMora;
import com.mibanquito.aportes.entity.ParametroPeriodo;
import com.mibanquito.aportes.entity.PeriodoAnualRef;
import com.mibanquito.aportes.repository.AporteInicialRepository;
import com.mibanquito.aportes.repository.AporteMensualRepository;
import com.mibanquito.aportes.repository.MultaMoraRepository;
import com.mibanquito.aportes.repository.ParametroPeriodoRepository;
import com.mibanquito.aportes.repository.PeriodoAnualRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 5 de tasks.md: estas 12 pruebas deben escribirse y FALLAR antes de que
 * exista una implementacion real de AporteService (Principio III). Con el
 * esqueleto de AporteService (metodos que lanzan UnsupportedOperationException)
 * las 12 fallan por esa razon; la Fase 6 las hace pasar una por una.
 */
@ExtendWith(MockitoExtension.class)
class AporteServiceTest {

    private static final Long SOCIO_ID = 1L;
    private static final Long PERIODO_ID = 10L;

    @Mock
    private AporteInicialRepository aporteInicialRepository;
    @Mock
    private AporteMensualRepository aporteMensualRepository;
    @Mock
    private MultaMoraRepository multaMoraRepository;
    @Mock
    private ParametroPeriodoRepository parametroPeriodoRepository;
    @Mock
    private PeriodoAnualRefRepository periodoAnualRefRepository;

    private AporteService aporteService;

    @BeforeEach
    void setUp() {
        aporteService = new AporteService(aporteInicialRepository, aporteMensualRepository,
                multaMoraRepository, parametroPeriodoRepository, periodoAnualRefRepository);

        // La mayoria de los tests no ejercitan el rechazo de periodo cerrado;
        // se deja disponible (lenient) para no forzar un stub explicito en
        // cada uno.
        lenient().when(periodoAnualRefRepository.findById(PERIODO_ID))
                .thenReturn(Optional.of(periodoAbierto(PERIODO_ID)));

        // save(...) devuelve el mismo objeto pasado, simulando persistencia.
        lenient().when(aporteMensualRepository.save(any(AporteMensual.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(multaMoraRepository.save(any(MultaMora.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private PeriodoAnualRef periodoAbierto(Long id) {
        return periodoConEstado(id, EstadoPeriodo.ABIERTO);
    }

    private PeriodoAnualRef periodoConEstado(Long id, EstadoPeriodo estado) {
        return new PeriodoAnualRef(id, estado);
    }

    private void mockIngreso(YearMonth mesIngreso) {
        AporteInicial aporteInicial = new AporteInicial(SOCIO_ID, PERIODO_ID,
                new BigDecimal("100.00"), mesIngreso.atDay(1));
        lenient().when(aporteInicialRepository.findFirstBySocioIdAndPeriodoIdOrderByFechaAsc(SOCIO_ID, PERIODO_ID))
                .thenReturn(Optional.of(aporteInicial));
    }

    private void mockParametroVigente(BigDecimal montoAporteMensual, BigDecimal montoMulta) {
        ParametroPeriodo parametro = new ParametroPeriodo(PERIODO_ID, montoAporteMensual, montoMulta,
                LocalDate.of(2020, 1, 1));
        lenient().when(parametroPeriodoRepository.findTopByPeriodoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        eq(PERIODO_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(parametro));
    }

    /** Simula que aun no existe ningun AporteMensual para socio+periodo. */
    private void mockSinAportesMensualesPrevios() {
        lenient().when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(eq(SOCIO_ID), eq(PERIODO_ID), any()))
                .thenReturn(Optional.empty());
        lenient().when(aporteMensualRepository.findBySocioIdAndPeriodoId(SOCIO_ID, PERIODO_ID))
                .thenReturn(List.of());
    }

    // ---- T018 -------------------------------------------------------

    @Test
    void calcularDeudaAcumulada_sinMora_devuelveSoloMesActual() {
        YearMonth mesActual = YearMonth.of(2026, 2);
        mockIngreso(mesActual);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, mesActual);

        assertEquals(0, new BigDecimal("50.00").compareTo(deuda));
        verify(multaMoraRepository, never()).save(any());
    }

    // ---- T019 (SC-001) -----------------------------------------------

    @Test
    void calcularDeudaAcumulada_unMesVencido_totalCientoCinco() {
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth febrero = YearMonth.of(2026, 2);
        mockIngreso(enero);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, febrero);

        assertEquals(0, new BigDecimal("105.00").compareTo(deuda));

        ArgumentCaptor<AporteMensual> aporteCaptor = ArgumentCaptor.forClass(AporteMensual.class);
        verify(aporteMensualRepository, times(2)).save(aporteCaptor.capture());
        List<AporteMensual> generados = aporteCaptor.getAllValues();
        assertThat(generados).extracting(AporteMensual::getMes).containsExactlyInAnyOrder(enero, febrero);
        assertThat(generados).allSatisfy(am -> assertEquals(0, new BigDecimal("50.00").compareTo(am.getMontoEsperado())));

        ArgumentCaptor<MultaMora> multaCaptor = ArgumentCaptor.forClass(MultaMora.class);
        verify(multaMoraRepository, times(1)).save(multaCaptor.capture());
        assertEquals(enero, multaCaptor.getValue().getMesGenerada());
        assertEquals(0, new BigDecimal("5.00").compareTo(multaCaptor.getValue().getMonto()));
    }

    // ---- T020 (SC-002) -----------------------------------------------

    @Test
    void calcularDeudaAcumulada_dosMesesVencidos_totalCientoSesenta() {
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth marzo = YearMonth.of(2026, 3);
        mockIngreso(enero);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, marzo);

        assertEquals(0, new BigDecimal("160.00").compareTo(deuda));
        verify(aporteMensualRepository, times(3)).save(any(AporteMensual.class));
        verify(multaMoraRepository, times(2)).save(any(MultaMora.class));
    }

    // ---- T021 (FR-012) -------------------------------------------------

    @Test
    void registrarAporteMensual_mesHastaConDosMesesVencidos_saldaAmbos() {
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth febrero = YearMonth.of(2026, 2);
        mockIngreso(enero);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        aporteService.registrarAporteMensual(SOCIO_ID, PERIODO_ID, febrero, new BigDecimal("105.00"),
                LocalDate.of(2026, 2, 15));

        // resolverMesesHasta crea y guarda cada mes (2 saves) y luego se
        // vuelve a guardar cada uno al marcarlo PAGADO (2 saves mas) = 4.
        // Como save() recibe la misma instancia mutada, las referencias
        // capturadas (sin duplicados) reflejan el estado final.
        ArgumentCaptor<AporteMensual> captor = ArgumentCaptor.forClass(AporteMensual.class);
        verify(aporteMensualRepository, atLeast(2)).save(captor.capture());
        List<AporteMensual> distintos = captor.getAllValues().stream().distinct().toList();
        assertThat(distintos).hasSize(2);
        assertThat(distintos).extracting(AporteMensual::getMes).containsExactlyInAnyOrder(enero, febrero);
        assertThat(distintos)
                .allSatisfy(am -> assertEquals(EstadoAporteMensual.PAGADO, am.getEstado()));
    }

    // ---- T022 (FR-009) -------------------------------------------------

    @Test
    void registrarAporteMensual_pagoParcialEnMora_lanzaExcepcion() {
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth febrero = YearMonth.of(2026, 2);
        mockIngreso(enero);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        assertThrows(PagoParcialNoPermitidoException.class, () ->
                aporteService.registrarAporteMensual(SOCIO_ID, PERIODO_ID, febrero, new BigDecimal("100.00"),
                        LocalDate.of(2026, 2, 15)));
    }

    // ---- T023 (FR-001) -------------------------------------------------

    @Test
    void registrarAporteInicial_montoNoPositivo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                aporteService.registrarAporteInicial(SOCIO_ID, PERIODO_ID, BigDecimal.ZERO, LocalDate.now()));
    }

    // ---- T024 (FR-010) -------------------------------------------------

    @Test
    void registrarAporteMensual_mesYaPagado_lanzaExcepcion() {
        YearMonth mes = YearMonth.of(2026, 2);
        mockIngreso(mes);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));

        AporteMensual yaPagado = new AporteMensual(SOCIO_ID, PERIODO_ID, mes, new BigDecimal("50.00"));
        yaPagado.marcarComoPagado(LocalDate.of(2026, 2, 10));
        lenient().when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(SOCIO_ID, PERIODO_ID, mes))
                .thenReturn(Optional.of(yaPagado));

        assertThrows(AporteMensualYaPagadoException.class, () ->
                aporteService.registrarAporteMensual(SOCIO_ID, PERIODO_ID, mes, new BigDecimal("50.00"),
                        LocalDate.of(2026, 2, 20)));
    }

    // ---- T025 (FR-011) -------------------------------------------------

    @Test
    void registrarAporte_periodoCerrado_lanzaExcepcion() {
        when(periodoAnualRefRepository.findById(PERIODO_ID))
                .thenReturn(Optional.of(periodoConEstado(PERIODO_ID, EstadoPeriodo.CERRADO)));

        assertThrows(PeriodoCerradoException.class, () ->
                aporteService.registrarAporteInicial(SOCIO_ID, PERIODO_ID, new BigDecimal("100.00"),
                        LocalDate.now()));

        assertThrows(PeriodoCerradoException.class, () ->
                aporteService.registrarAporteMensual(SOCIO_ID, PERIODO_ID, YearMonth.now(),
                        new BigDecimal("50.00"), LocalDate.now()));
    }

    // ---- T026 (FR-007 / FR-013) -----------------------------------------

    @Test
    void calcularDeudaAcumulada_socioIngresoMitadAnio_noGeneraMesesPrevios() {
        YearMonth ingreso = YearMonth.of(2026, 7);
        mockIngreso(ingreso);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, ingreso);

        assertEquals(0, new BigDecimal("50.00").compareTo(deuda));
        verify(aporteMensualRepository, times(1)).save(any(AporteMensual.class));
    }

    // ---- T027 (FR-003) -------------------------------------------------

    @Test
    void calcularDeudaAcumulada_usaParametroPeriodoVigente_noHardcodeado() {
        Long otroPeriodoId = 20L;
        YearMonth mes = YearMonth.of(2026, 5);

        // Periodo 10: parametro 50
        mockIngreso(mes);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));
        mockSinAportesMensualesPrevios();
        BigDecimal deudaPeriodo10 = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, mes);

        // Periodo 20: parametro distinto (80)
        AporteInicial ingresoOtroPeriodo = new AporteInicial(SOCIO_ID, otroPeriodoId,
                new BigDecimal("100.00"), mes.atDay(1));
        when(aporteInicialRepository.findFirstBySocioIdAndPeriodoIdOrderByFechaAsc(SOCIO_ID, otroPeriodoId))
                .thenReturn(Optional.of(ingresoOtroPeriodo));
        ParametroPeriodo parametroOtroPeriodo = new ParametroPeriodo(otroPeriodoId,
                new BigDecimal("80.00"), new BigDecimal("8.00"), LocalDate.of(2020, 1, 1));
        when(parametroPeriodoRepository.findTopByPeriodoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        eq(otroPeriodoId), any(LocalDate.class)))
                .thenReturn(Optional.of(parametroOtroPeriodo));
        when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(eq(SOCIO_ID), eq(otroPeriodoId), any()))
                .thenReturn(Optional.empty());

        BigDecimal deudaPeriodo20 = aporteService.calcularDeudaAcumulada(SOCIO_ID, otroPeriodoId, mes);

        assertEquals(0, new BigDecimal("50.00").compareTo(deudaPeriodo10));
        assertEquals(0, new BigDecimal("80.00").compareTo(deudaPeriodo20));
    }

    // ---- T028 (FR-008) -------------------------------------------------

    @Test
    void resolverMesesHasta_cambioParametroAMitadPeriodo_noRecalculaMesesYaGenerados() {
        YearMonth enero = YearMonth.of(2026, 1);
        mockIngreso(enero);

        // Enero ya fue generado y congelado con el parametro ANTIGUO (50/5).
        AporteMensual eneroYaGenerado = new AporteMensual(SOCIO_ID, PERIODO_ID, enero, new BigDecimal("50.00"));
        when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(SOCIO_ID, PERIODO_ID, enero))
                .thenReturn(Optional.of(eneroYaGenerado));

        // El parametro "vigente ahora" ya cambio a 70/7, pero enero no debe
        // recalcularse con este valor.
        mockParametroVigente(new BigDecimal("70.00"), new BigDecimal("7.00"));

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, enero);

        assertEquals(0, new BigDecimal("50.00").compareTo(deuda));
        assertEquals(0, new BigDecimal("50.00").compareTo(eneroYaGenerado.getMontoEsperado()));
    }

    // ---- T029 (FR-006) -------------------------------------------------

    @Test
    void obtenerTotalMultasPeriodo_sumaMultasDelPeriodo_totalCorrecto() {
        when(multaMoraRepository.sumMontoByPeriodoId(PERIODO_ID)).thenReturn(new BigDecimal("15.00"));

        BigDecimal total = aporteService.obtenerTotalMultasPeriodo(PERIODO_ID);

        assertEquals(0, new BigDecimal("15.00").compareTo(total));
    }

    // ---- T048 (remediacion /speckit.analyze, hallazgo B1) ---------------
    // Bug confirmado ejecutando el servicio real: un AporteMensual generado
    // en una llamada anterior (cuando aun no estaba vencido) nunca recibia
    // su multa al volverse moroso en una llamada posterior y separada.

    @Test
    void calcularDeudaAcumulada_mesPreexistenteSeVuelveVencido_generaMultaRetroactivamente() {
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth febrero = YearMonth.of(2026, 2);
        YearMonth marzo = YearMonth.of(2026, 3);
        mockIngreso(enero);
        mockParametroVigente(new BigDecimal("50.00"), new BigDecimal("5.00"));

        // Enero ya existe (PENDIENTE), generado en una llamada anterior sin
        // multa porque en ese momento no estaba vencido.
        AporteMensual eneroExistente = new AporteMensual(SOCIO_ID, PERIODO_ID, enero, new BigDecimal("50.00"));
        when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(SOCIO_ID, PERIODO_ID, enero))
                .thenReturn(Optional.of(eneroExistente));
        when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(SOCIO_ID, PERIODO_ID, febrero))
                .thenReturn(Optional.empty());
        when(aporteMensualRepository.findBySocioIdAndPeriodoIdAndMes(SOCIO_ID, PERIODO_ID, marzo))
                .thenReturn(Optional.empty());
        when(multaMoraRepository.findByAporteMensual(eneroExistente)).thenReturn(Optional.empty());

        BigDecimal deuda = aporteService.calcularDeudaAcumulada(SOCIO_ID, PERIODO_ID, marzo);

        // Ene(50)+Feb(50)+Mar(50)+2 multas(5 c/u) = 160.00 (igual que T020,
        // pero con enero generado en una llamada previa y separada).
        assertEquals(0, new BigDecimal("160.00").compareTo(deuda));
        verify(multaMoraRepository).save(argThat(m ->
                m.getAporteMensual() == eneroExistente && enero.equals(m.getMesGenerada())));
    }
}
