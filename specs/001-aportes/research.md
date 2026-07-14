# Research: Aportes

**Phase 0 output** — el stack completo (Java 21, Spring Boot 3, MySQL 8 + Flyway,
Thymeleaf + Bootstrap 5, JUnit 5 + Mockito) y la convención de paquetes
(`com.mibanquito.aportes.{entity,repository,service,dto,controller}`) ya están
fijados por `.specify/memory/constitution.md` y `docs/arquitectura-general.md`. No
quedan marcadores `NEEDS CLARIFICATION` en el Technical Context. Este documento
resuelve las decisiones de diseño específicas del dominio Aportes.

## 1. Representación de dinero (Peruvian soles)

**Decision**: Todo campo monetario usa `BigDecimal` con `scale = 2` y
`RoundingMode.HALF_UP`, mapeado en JPA como `@Column(precision = 10, scale = 2)`.

**Rationale**: Principio VIII prohíbe `float`/`double`. `BigDecimal` con escala
fija permite comparación exacta (`compareTo`), imprescindible para que
`registrarAporteMensual` rechace cualquier monto que no coincida exactamente con
la deuda calculada.

**Alternatives considered**: enteros en centavos — descartado, sin ventaja para
el volumen de este sistema y peor legibilidad en las vistas Thymeleaf.

## 2. Historial inmutable de `montoEsperado` y `MultaMora.monto` (FR-008)

**Decision**: `ParametroPeriodo` es una tabla de historial (`periodo_id`,
`monto_aporte_mensual`, `monto_multa`, `vigente_desde`). Cuando `AporteService`
genera o resuelve un `AporteMensual` para un mes M, congela el
`ParametroPeriodo` vigente en ese momento en `AporteMensual.montoEsperado` y,
si corresponde, en `MultaMora.monto`. Estos valores **no se recalculan** aunque
`ParametroPeriodo` cambie después — instrucción explícita confirmada por el
usuario y ya requerida por FR-008.

**Rationale**: Es la única forma de que `calcularDeudaAcumulada` reproduzca
exactamente S/105 (Ene+Feb a S/50 + 1 multa S/5) y S/160 (Ene+Feb+Mar a S/50 +
2 multas S/5) sin importar si el parámetro del periodo cambió entre medio.

**Alternatives considered**: recalcular con el parámetro vigente al momento del
pago — rechazado explícitamente por el usuario y contradice FR-008 y el edge
case del spec ("los meses ya vencidos mantienen el monto vigente cuando
vencieron").

## 3. `registrarAporteMensual` y rechazo de pago parcial (`PagoParcialNoPermitidoException`)

**Decision**: `AporteService.registrarAporteMensual` calcula primero
`calcularDeudaAcumulada(socio, periodo, mesActual)`. Si el socio tiene algún mes
`PENDIENTE` anterior al mes actual (está en mora) y el monto recibido no
coincide **exactamente** con la deuda acumulada total, lanza
`PagoParcialNoPermitidoException` (unchecked, `RuntimeException`) antes de
persistir nada. Si el monto coincide exactamente, marca todos los
`AporteMensual` cubiertos como `PAGADO` y las `MultaMora` asociadas como
saldadas, en una única transacción (`@Transactional`).

**Rationale**: FR-009 exige rechazar tanto pagos parciales como sobrepagos; una
excepción de dominio explícita (en vez de un booleano o `Optional`) permite que
`AporteController` la traduzca a un mensaje de error claro sin que el Service
conozca detalles de HTTP/vistas (separación de capas de
`docs/arquitectura-general.md`).

**Alternatives considered**: devolver un resultado (`Either`/`ResultDTO`) en vez
de lanzar excepción — descartado por consistencia con el resto del monolito,
donde las reglas de negocio duras (Principio XI, "barrera dura... nunca solo una
advertencia") se expresan como excepciones que abortan la transacción.

## 4. Rechazo de operaciones sobre periodo cerrado (FR-011)

**Decision**: `AporteService` valida `periodo.getEstado() == CERRADO` al inicio
de `registrarAporteInicial` y `registrarAporteMensual`, lanzando una excepción
de dominio (`PeriodoCerradoException`) antes de cualquier escritura.

**Rationale**: Principio XIV es transversal a varios módulos futuros
(Préstamos, Cierre anual); centralizar la comprobación en el Service (no en el
Controller ni en la vista) asegura que se cumpla sin importar el punto de
entrada.

**Alternatives considered**: constraint a nivel de base de datos (trigger) —
descartado por complejidad adicional no justificada (Principio IV,
simplicidad); la validación en Service es suficiente dado que todas las
escrituras pasan por `AporteService`.

## 5. Unicidad socio+periodo+mes (FR-010)

**Decision**: índice único `UNIQUE (socio_id, periodo_id, mes)` en la migración
Flyway de `aporte_mensual`, además de la comprobación en `AporteService` antes
de insertar.

**Rationale**: doble barrera — el Service da el mensaje de error de negocio
claro, el índice de BD previene condiciones de carrera si dos administradores
registran el mismo pago casi simultáneamente.

**Alternatives considered**: solo validación en Service — descartado por riesgo
de condición de carrera sin barrera atómica en BD.

## 6. Testing test-first de `AporteService` (Principio III)

**Decision**: `AporteServiceTest` (JUnit 5 + Mockito, mocks de
`AporteMensualRepository` y `ParametroPeriodoRepository`) se escribe primero,
con al menos estos casos, y debe fallar contra un `AporteService` vacío antes
de implementarlo:
1. 1 mes vencido (enero) pagado en febrero → deuda total exacta S/105.
2. 2 meses vencidos (enero y febrero) pagados en marzo → deuda total exacta
   S/160.
3. Pago que no coincide exactamente con la deuda (mayor o menor) estando en
   mora → lanza `PagoParcialNoPermitidoException`.

Solo después de que estas pruebas pasen se escribe `AporteController`.

**Rationale**: exigencia directa del usuario y del Principio III; estas pruebas
son la verificación ejecutable de SC-001 y SC-002.

**Alternatives considered**: prueba de integración con Testcontainers/MySQL
real — fuera de este plan (no solicitada; constitución la marca opcional).

## Resumen de resolución de NEEDS CLARIFICATION

Ninguno — el Technical Context no contenía marcadores pendientes. Las 6
decisiones anteriores son de diseño de dominio y de la capa Service, no de
selección de stack ni de estructura de paquetes (ya fijada por
`docs/arquitectura-general.md`).
