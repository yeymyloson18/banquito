package com.mibanquito.aportes.repository;

import com.mibanquito.aportes.entity.AporteMensual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface AporteMensualRepository extends JpaRepository<AporteMensual, Long> {

    List<AporteMensual> findBySocioIdAndPeriodoId(Long socioId, Long periodoId);

    Optional<AporteMensual> findBySocioIdAndPeriodoIdAndMes(Long socioId, Long periodoId, YearMonth mes);
}
