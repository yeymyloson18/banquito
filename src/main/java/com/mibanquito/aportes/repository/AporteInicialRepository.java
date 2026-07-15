package com.mibanquito.aportes.repository;

import com.mibanquito.aportes.entity.AporteInicial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AporteInicialRepository extends JpaRepository<AporteInicial, Long> {

    /**
     * Un socio tiene, como maximo, un AporteInicial por periodo (US1); su
     * fecha es la fuente del mes de ingreso (FR-013). Si por alguna razon
     * existiera mas de uno, se toma el mas antiguo.
     */
    Optional<AporteInicial> findFirstBySocioIdAndPeriodoIdOrderByFechaAsc(Long socioId, Long periodoId);
}
