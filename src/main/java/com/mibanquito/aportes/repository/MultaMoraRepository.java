package com.mibanquito.aportes.repository;

import com.mibanquito.aportes.entity.MultaMora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MultaMoraRepository extends JpaRepository<MultaMora, Long> {

    List<MultaMora> findByAporteMensualIdIn(List<Long> aporteMensualIds);

    /**
     * Agregado por periodo, expuesto para que el futuro modulo Caja sume las
     * multas a la caja del banco (FR-006). Ver data-model.md Integracion con
     * el modulo Caja.
     */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MultaMora m WHERE m.aporteMensual.periodoId = :periodoId")
    BigDecimal sumMontoByPeriodoId(@Param("periodoId") Long periodoId);
}
