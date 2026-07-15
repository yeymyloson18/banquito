package com.mibanquito.aportes.repository;

import com.mibanquito.aportes.entity.ParametroPeriodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ParametroPeriodoRepository extends JpaRepository<ParametroPeriodo, Long> {

    /**
     * Resuelve el ParametroPeriodo vigente para un periodo en una fecha dada:
     * la fila con vigenteDesde mas reciente que sea <= fecha.
     */
    Optional<ParametroPeriodo> findTopByPeriodoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            Long periodoId, LocalDate fecha);
}
