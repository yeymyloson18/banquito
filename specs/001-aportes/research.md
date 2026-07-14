# Research: Aportes

**Phase 0 output** — todas las decisiones técnicas del stack ya estaban fijadas
por el usuario y la constitución (Java 21, Spring Boot 3, MySQL 8, Thymeleaf +
Bootstrap 5, JUnit 5 + Mockito, Maven), por lo que no quedan marcadores `NEEDS
CLARIFICATION` en el Technical Context. Este documento resuelve las decisiones de
diseño específicas del dominio (Aportes) necesarias antes de modelar datos.

## 1. Representación de dinero (Peruvian soles)

**Decision**: Todos los campos monetarios usan `BigDecimal` con `scale = 2` y
`RoundingMode.HALF_UP`, mapeados en JPA como `@Column(precision = 10, scale = 2)`.

**Rationale**: El Principio VIII de la constitución prohíbe `float`/`double` para
cualquier monto, tasa o saldo. `BigDecimal` con escala fija evita errores de
redondeo binario y permite comparaciones exactas (`compareTo`), imprescindible
para FR-009 (el pago debe coincidir exactamente con la deuda calculada, ni más ni
menos).

**Alternatives considered**: `long` en centavos — descartado porque complica la
legibilidad en las plantillas Thymeleaf y no aporta ventaja sobre `BigDecimal`
con escala fija para los volúmenes de este sistema (decenas de socios).

## 2. Modelado de entidades con historial de parámetros por periodo (ParametroPeriodo)

**Decision**: `ParametroPeriodo` es una tabla de historial (no un valor único por
periodo): cada fila tiene `periodoId`, `montoAporteMensual`, `montoMulta` y
`vigenteDesde`. El "monto vigente" para un mes dado es el de la fila con
`vigenteDesde` más reciente que sea `<=` la fecha de vencimiento de ese mes.

**Rationale**: El edge case del spec ("cambio del monto del aporte o la multa a
mitad de periodo → solo aplica hacia adelante; los meses ya vencidos mantienen el
monto vigente cuando vencieron", FR-008) exige que el sistema pueda reconstruir
qué monto regía en cualquier mes pasado. Un único valor mutable por periodo
perdería ese historial en cuanto se actualice.

**Alternatives considered**: Copiar el monto vigente directamente en
`AporteMensual.montoEsperado` al momento de generarse el mes (snapshot) —
complementario, no alternativo: se adopta también esto (ver dato en
`data-model.md`) como la fuente de verdad ya congelada para ese mes, y
`ParametroPeriodo` queda como historial de configuración para generar nuevos
meses correctamente.

## 3. Validación de pago exacto (FR-009) y rechazo de duplicados (FR-010)

**Decision**: La validación de "pago exacto" y "no duplicado" se implementa en la
capa de servicio (`AporteMensualService`, `MorosidadService`), no solo como
constraint de base de datos. La unicidad socio+periodo+mes sí se refuerza además
con un índice único a nivel de esquema (`UNIQUE (socio_id, periodo_id, mes)` en
Flyway) como segunda barrera.

**Rationale**: Principio XI-adjacent — las reglas de negocio críticas deben ser
una barrera dura en el código, no solo una validación de UI. Un índice único a
nivel de BD previene condiciones de carrera (dos registros concurrentes para el
mismo mes) que una validación solo en servicio no cubriría de forma atómica.

**Alternatives considered**: Solo validar en servicio sin constraint de BD —
descartado por riesgo de condición de carrera con dos administradores
registrando el mismo pago casi simultáneamente.

## 4. Rechazo de operaciones sobre periodo cerrado (FR-011)

**Decision**: Un `@Aspect` o filtro de servicio común (`PeriodoGuard`) valida el
estado del periodo antes de cualquier operación de escritura sobre `Aporte*`,
lanzando una excepción de negocio (`PeriodoCerradoException`) capturada por un
`@ControllerAdvice` que la traduce a un mensaje de error en la vista Thymeleaf.

**Rationale**: Principio XIV (periodo cerrado inmutable) es transversal a varios
módulos (no solo Aportes); centralizar la validación evita duplicar la
comprobación en cada servicio y facilita que otros módulos (Préstamos, Cierre
anual) reutilicen el mismo guard cuando se implementen.

**Alternatives considered**: Duplicar el `if (periodo.isCerrado())` en cada
método de servicio — descartado por violar DRY y por el riesgo de que un futuro
módulo olvide la comprobación.

## 5. Testing de lógica financiera (Principio III, TDD)

**Decision**: `MorosidadServiceTest` implementa primero los dos casos exactos del
spec (S/105 con 1 mes vencido, S/160 con 2 meses vencidos) como pruebas JUnit 5
que fallan contra un `MorosidadService` vacío, antes de escribir la
implementación. Mockito se usa para simular `AporteMensualRepository` y
`ParametroPeriodoRepository` sin necesidad de base de datos real en pruebas
unitarias.

**Rationale**: Exigencia directa del Principio III y del FR de trazabilidad
(Principio V): estas pruebas son la verificación ejecutable de SC-001 y SC-002.

**Alternatives considered**: Prueba de integración con Testcontainers/MySQL real
— se deja fuera de este plan por no haber sido solicitada por el usuario en el
stack de testing (constitución la marca como opcional); puede añadirse después
sin cambiar el diseño del servicio.

## Resumen de resolución de NEEDS CLARIFICATION

Ninguno — el Technical Context no contenía marcadores pendientes. Las 5
decisiones anteriores son de diseño de dominio, no de selección de stack.
