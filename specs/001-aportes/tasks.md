---

description: "Task list for feature 001-aportes"
---

# Tasks: Aportes

**Input**: Design documents from `specs/001-aportes/` (`spec.md`, `plan.md`, `data-model.md`, `research.md`, `contracts/`, `quickstart.md`)

**Tests**: Solicitados explícitamente por el usuario y exigidos por el Principio
III de la constitución (test-first para lógica de negocio) — las pruebas de
`AporteServiceTest` (Fase 5) deben escribirse y **fallar** antes de implementar
`AporteService` (Fase 6).

**Organización**: A diferencia del orden por historia de usuario del template
estándar, este `tasks.md` sigue el orden estricto por capas que pidió el
usuario (Setup → Migraciones → Entidades → Repositorios → Tests → Service →
DTO → Controller → Verificación), porque las 3 historias de usuario comparten
una única clase `AporteService` y no pueden entregarse ni testearse como
incrementos aislados sin romper el test-first. Cada tarea igual se etiqueta
`[USx]` cuando pertenece claramente a una historia, para trazabilidad
(Principio V).

**Nota de alcance (migraciones)**: `spec.md` declara como dependencia "Registro
de socios" y "Apertura de periodo anual", pero ninguno de esos módulos existe
aún en el repo. Sin tablas `socio` y `periodo_anual`, las FK reales de este
feature no podrían crearse y `mvnw clean test` fallaría. Se añaden 2
migraciones stub mínimas (T003, T004) para desbloquear esto; los módulos
reales deberán `ALTER TABLE` estas tablas al implementarse, sin tocar las FK ya
creadas por Aportes.

**Remediación de `/speckit.analyze` (2 rondas)**: ronda 1 resolvió los 3
hallazgos CRITICAL (C1 seguridad por rol, G1 multas→caja, U1 ambigüedad de
pago multi-mes), los 2 HIGH (G2 mes de ingreso, I1 test-first de FR-010/FR-011)
y los 3 MEDIUM (M1 parametrización no hardcodeada, M2 detalle de concepto, M3
"RF-16"). Ronda 2 detectó 2 CRITICAL adicionales — FR-006 y FR-008 son reglas
financieras (Principio II) que tenían tarea de implementación pero **no**
prueba unitaria dedicada — resueltos ahora con T028 y T029 (nuevas). Fase 5
pasó de 10 a 12 tests; todo lo posterior a la Fase 5 se renumeró (+2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Se puede hacer en paralelo (archivo distinto, sin dependencias pendientes)
- **[Story]**: US1 (aporte inicial), US2 (aporte mensual), US3 (morosidad)
- Fases de infraestructura compartida (Setup, Migraciones, Entidades, Repositorios, Verificación) no llevan etiqueta de historia, por convención del template

## Path Conventions

Monolito Maven único, paquete `com.mibanquito.aportes` con subpaquetes por capa
(`entity`, `repository`, `service`, `dto`, `controller`), conforme a
`docs/arquitectura-general.md` y `plan.md` → Project Structure.

---

## Phase 1: Setup

**Purpose**: Inicialización del proyecto Maven y configuración de Flyway

- [x] T001 Crear `pom.xml` con las dependencias del stack obligatorio: Spring Boot 3 Starter Web, Data JPA, Security, Validation; MySQL Connector/J; Flyway (`flyway-core` + `flyway-mysql`); Thymeleaf; JUnit 5; Mockito — `pom.xml`
- [x] T002 Configurar `src/main/resources/application.yml` con datasource MySQL 8 y `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration` — `src/main/resources/application.yml`

**Nota (FR-014)**: se asume que `@EnableMethodSecurity` y la configuración
base de autenticación/roles las provee el módulo Autenticación (fuera de
alcance de este feature); Aportes solo consume `@PreAuthorize` en sus rutas
(Fase 8).

**Checkpoint**: proyecto compila (`mvnw compile`) con el stack correcto configurado.

---

## Phase 2: Migraciones Flyway (Foundational)

**Purpose**: Esquema de base de datos para las 4 tablas de Aportes y sus FK

**⚠️ CRITICAL**: Ninguna tarea de Entidades/Repositorios puede empezar hasta que estas migraciones existan

- [x] T003 [P] Crear migración stub de `periodo_anual` (`id BIGINT PK`, `estado VARCHAR(10)` con valores `ABIERTO`/`CERRADO`) — `src/main/resources/db/migration/V1__crear_periodo_anual_stub.sql`
- [x] T004 [P] Crear migración stub de `socio` (`id BIGINT PK`) — `src/main/resources/db/migration/V2__crear_socio_stub.sql`
- [x] T005 Crear migración de `parametro_periodo` (`id`, `periodo_id BIGINT FK→periodo_anual`, `monto_aporte_mensual DECIMAL(10,2)`, `monto_multa DECIMAL(10,2)`, `vigente_desde DATE`) — `src/main/resources/db/migration/V3__crear_parametro_periodo.sql` (depende de T003)
- [x] T006 Crear migración de `aporte_inicial` (`id`, `socio_id BIGINT FK→socio`, `periodo_id BIGINT FK→periodo_anual`, `monto DECIMAL(10,2)`, `fecha DATE`) — `src/main/resources/db/migration/V4__crear_aporte_inicial.sql` (depende de T003, T004)
- [x] T007 Crear migración de `aporte_mensual` (`id`, `socio_id BIGINT FK→socio`, `periodo_id BIGINT FK→periodo_anual`, `mes DATE` o `CHAR(7)`, `monto_esperado DECIMAL(10,2)`, `fecha_pago DATE NULL`, `estado VARCHAR(10)`, `UNIQUE (socio_id, periodo_id, mes)` — FR-010) — `src/main/resources/db/migration/V5__crear_aporte_mensual.sql` (depende de T003, T004)
- [x] T008 Crear migración de `multa_mora` (`id`, `aporte_mensual_id BIGINT FK→aporte_mensual UNIQUE`, `monto DECIMAL(10,2)`, `mes_generada DATE` o `CHAR(7)`) — `src/main/resources/db/migration/V6__crear_multa_mora.sql` (depende de T007)

**Checkpoint**: `mvnw flyway:migrate` (o el arranque de la app) aplica las 6 migraciones sin error.

---

## Phase 3: Entidades JPA (Foundational)

**Purpose**: Mapeo objeto-relacional de las 4 tablas nuevas

- [x] T009 [P] Crear enum `EstadoAporteMensual` (`PENDIENTE`, `PAGADO`) — `src/main/java/com/mibanquito/aportes/entity/EstadoAporteMensual.java`
- [x] T010 [P] Crear entidad `ParametroPeriodo` (`BigDecimal montoAporteMensual`, `BigDecimal montoMulta`, `LocalDate vigenteDesde`, FK `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/ParametroPeriodo.java`
- [x] T011 [P] Crear entidad `AporteInicial` (`BigDecimal monto`, `LocalDate fecha`, FK `socioId`, `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/AporteInicial.java` — su `fecha` es la fuente del mes de ingreso del socio (FR-013)
- [x] T012 [US2] Crear entidad `AporteMensual` (`YearMonth mes`, `BigDecimal montoEsperado`, `LocalDate fechaPago`, `EstadoAporteMensual estado`, FK `socioId`, `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/AporteMensual.java` (depende de T009 por el enum)
- [x] T013 [US3] Crear entidad `MultaMora` (`BigDecimal monto`, `YearMonth mesGenerada`, FK única `aporteMensualId`) — `src/main/java/com/mibanquito/aportes/entity/MultaMora.java` (depende de T012)

**Checkpoint**: las 5 clases compilan y mapean 1:1 a las tablas de la Fase 2.

---

## Phase 4: Repositorios (Foundational)

**Purpose**: Acceso a datos Spring Data JPA, sin lógica de negocio

- [x] T014 [P] Crear `ParametroPeriodoRepository extends JpaRepository<ParametroPeriodo, Long>` (incluye método para resolver el `ParametroPeriodo` vigente a una fecha dada, p. ej. `findTopByPeriodoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc`) — `src/main/java/com/mibanquito/aportes/repository/ParametroPeriodoRepository.java` (depende de T010)
- [x] T015 [P] [US1] Crear `AporteInicialRepository extends JpaRepository<AporteInicial, Long>` (incluye `findBySocioIdAndPeriodoId` para resolver el mes de ingreso, FR-013) — `src/main/java/com/mibanquito/aportes/repository/AporteInicialRepository.java` (depende de T011)
- [x] T016 [P] [US2] Crear `AporteMensualRepository extends JpaRepository<AporteMensual, Long>` con `findBySocioIdAndPeriodoId(...)` y `findBySocioIdAndPeriodoIdAndMes(...)` para soportar `calcularDeudaAcumulada` — `src/main/java/com/mibanquito/aportes/repository/AporteMensualRepository.java` (depende de T012)
- [x] T017 [P] [US3] Crear `MultaMoraRepository extends JpaRepository<MultaMora, Long>` con `findByAporteMensualIdIn(...)` **y** un método de agregación `sumMontoByPeriodoId(periodoId): BigDecimal` (FR-006, ver `data-model.md` §Integración con Caja) — `src/main/java/com/mibanquito/aportes/repository/MultaMoraRepository.java` (depende de T013)

**Checkpoint**: Fundación completa — Fase 5 (tests) puede empezar.

---

## Phase 5: Tests de `AporteService` (deben fallar primero — Principio III)

**Purpose**: Verificación ejecutable de SC-001/SC-002/SC-005 y de todas las
reglas de rechazo y reglas financieras (FR-003, FR-006, FR-007, FR-008,
FR-009, FR-010, FR-011, FR-013), escrita ANTES de que exista `AporteService`.

**⚠️ CRITICAL**: Estas 12 pruebas deben compilar contra un `AporteService` aún no implementado (o vacío) y **fallar** antes de la Fase 6. Todas viven en el mismo archivo, por lo que no son paralelizables entre sí.

**Nota de remediación (I1 y C1/C2 de `/speckit.analyze`)**: la primera
revisión de este archivo solo probaba el rechazo de pago parcial (FR-009)
antes de implementar el Service, dejando FR-010/FR-011 sin test previo (I1,
ya resuelto con T024/T025). La segunda ronda de análisis detectó que FR-006
(multas→caja) y FR-008 (historial inmutable ante cambio de parámetro a mitad
de periodo) tenían tarea de implementación pero ninguna prueba unitaria
dedicada, violando el Principio II — T028 y T029 (nuevas) cierran ese hueco.

- [x] T018 [US3] Test: socio sin meses vencidos — `calcularDeudaAcumulada` devuelve solo el aporte del mes actual — `src/test/java/com/mibanquito/aportes/service/AporteServiceTest.java` (método `calcularDeudaAcumulada_sinMora_devuelveSoloMesActual`)
- [x] T019 [US3] Test: 1 mes vencido (enero, S/50) pagado en febrero (S/50) + 1 multa (S/5) = **S/105 exacto** (SC-001); verificar también que el desglose incluye el concepto de cada línea (aporte de enero, aporte de febrero, multa de enero — FR-005) — mismo archivo (método `calcularDeudaAcumulada_unMesVencido_totalCientoCinco`)
- [x] T020 [US3] Test: 2 meses vencidos (enero y febrero, S/50 c/u) pagados en marzo (S/50) + 2 multas (S/5 c/u) = **S/160 exacto** (SC-002); verificar también el desglose por concepto (FR-005) — mismo archivo (método `calcularDeudaAcumulada_dosMesesVencidos_totalCientoSesenta`)
- [x] T021 [US2] Test: `registrarAporteMensual` con `mesHasta` cubriendo enero y febrero vencidos marca **ambos** `AporteMensual` como `PAGADO` en una sola llamada y sus multas quedan saldadas (FR-012, resuelve la ambigüedad U1 de `/speckit.analyze`) — mismo archivo (método `registrarAporteMensual_mesHastaConDosMesesVencidos_saldaAmbos`)
- [x] T022 [US3] Test: socio en mora, `registrarAporteMensual` con monto que no coincide exactamente con la deuda acumulada (mayor o menor) lanza `PagoParcialNoPermitidoException` (FR-009) — mismo archivo (método `registrarAporteMensual_pagoParcialEnMora_lanzaExcepcion`)
- [x] T023 [US1] Test: `registrarAporteInicial` con `monto <= 0` falla la validación (FR-001) — mismo archivo (método `registrarAporteInicial_montoNoPositivo_lanzaExcepcion`)
- [x] T024 [US2] Test: `registrarAporteMensual` sobre un mes ya `PAGADO` lanza `AporteMensualYaPagadoException` (FR-010, resuelve I1) — mismo archivo (método `registrarAporteMensual_mesYaPagado_lanzaExcepcion`)
- [x] T025 Test: `registrarAporteInicial` y `registrarAporteMensual` sobre un periodo `CERRADO` lanzan `PeriodoCerradoException` (FR-011, resuelve I1) — mismo archivo (método `registrarAporte_periodoCerrado_lanzaExcepcion`)
- [x] T026 [US2] Test: un socio cuyo `AporteInicial.fecha` es de mitad de año no genera `AporteMensual` para los meses anteriores a su ingreso (FR-007, FR-013, resuelve G2) — mismo archivo (método `calcularDeudaAcumulada_socioIngresoMitadAnio_noGeneraMesesPrevios`)
- [x] T027 Test: `calcularDeudaAcumulada` usa el `ParametroPeriodo` vigente correspondiente a cada periodo (dos periodos con montos distintos deben producir totales distintos, nunca un valor hardcodeado) (FR-003, resuelve M1) — mismo archivo (método `calcularDeudaAcumulada_usaParametroPeriodoVigente_noHardcodeado`)
- [x] T028 Test **(nuevo, resuelve C2 de la 2ª ronda de `/speckit.analyze`)**: cambiar `ParametroPeriodo` (nuevo `montoAporteMensual`/`montoMulta` con `vigenteDesde` posterior) **después** de que un `AporteMensual` ya fue generado y congelado NO debe alterar su `montoEsperado` ni el `monto` de su `MultaMora` ya existente (FR-008) — mismo archivo (método `resolverMesesHasta_cambioParametroAMitadPeriodo_noRecalculaMesesYaGenerados`)
- [x] T029 Test **(nuevo, resuelve C1 de la 2ª ronda de `/speckit.analyze`)**: `obtenerTotalMultasPeriodo` suma correctamente el monto de todas las `MultaMora` de un periodo, incluyendo cuando hay multas de varios socios (FR-006) — mismo archivo (método `obtenerTotalMultasPeriodo_sumaMultasDelPeriodo_totalCorrecto`)

**Checkpoint**: `mvnw test -Dtest=AporteServiceTest` — las 12 pruebas existen y **fallan** (no hay implementación aún).

---

## Phase 6: Implementar `AporteService` (hasta que las 12 pruebas de la Fase 5 pasen)

- [x] T030 [P] [US2] Crear excepción `PagoParcialNoPermitidoException` (RuntimeException) — `src/main/java/com/mibanquito/aportes/service/PagoParcialNoPermitidoException.java`
- [x] T031 [P] Crear excepción `PeriodoCerradoException` (RuntimeException) — `src/main/java/com/mibanquito/aportes/service/PeriodoCerradoException.java`
- [x] T032 [P] [US2] Crear excepción `AporteMensualYaPagadoException` (RuntimeException) — `src/main/java/com/mibanquito/aportes/service/AporteMensualYaPagadoException.java`
- [x] T033 [US1] Implementar `AporteService.registrarAporteInicial` (valida `monto > 0`, rechaza si periodo `CERRADO` vía `PeriodoCerradoException`, persiste `AporteInicial`) — `src/main/java/com/mibanquito/aportes/service/AporteService.java` (depende de T015, T030, T031)
- [x] T034 [US3] Implementar el método interno `resolverMesesHasta(socioId, periodoId, mesHasta)`: genera de forma perezosa los `AporteMensual` `PENDIENTE` faltantes entre el mes de ingreso del socio (derivado de `AporteInicial.fecha`, FR-013) y `mesHasta`, congelando `montoEsperado`/`MultaMora.monto` desde el `ParametroPeriodo` vigente en cada mes (FR-007, FR-008) **y nunca recalculando meses ya generados aunque `ParametroPeriodo` cambie después** (verificado por T028) — mismo archivo (depende de T014, T015, T016, T017, T033)
- [x] T035 [US3] Implementar `AporteService.calcularDeudaAcumulada` (usa `resolverMesesHasta`; suma `montoEsperado` de `AporteMensual` PENDIENTE con mes `<= mesHasta` + `MultaMora` asociadas) — mismo archivo (depende de T034)
- [x] T036 [US2] Implementar `AporteService.registrarAporteMensual(socioId, periodoId, mesHasta, montoPagado, fecha)`: rechaza duplicado con `AporteMensualYaPagadoException` si `mesHasta` ya está `PAGADO` (FR-010); rechaza si periodo `CERRADO` (FR-011); usa `calcularDeudaAcumulada` y rechaza con `PagoParcialNoPermitidoException` si el monto no coincide exacto (FR-009); si coincide, marca **todos** los `AporteMensual` con mes `<= mesHasta` en `PENDIENTE` como `PAGADO` en una única transacción `@Transactional` (FR-012) — mismo archivo (depende de T035)
- [x] T037 Implementar `AporteService.obtenerTotalMultasPeriodo(periodoId): BigDecimal` (verificado por T029): envuelve `MultaMoraRepository.sumMontoByPeriodoId`, expuesto para que el futuro módulo Caja sume las multas a la caja del banco (FR-006) — mismo archivo (depende de T017)
- [x] T038 Ejecutar `mvnw test -Dtest=AporteServiceTest` y confirmar que las 12 pruebas (T018–T029) **pasan** — checkpoint de verificación, sin archivo nuevo

**Checkpoint**: `AporteService` completo y probado. El `AporteController` (Fase 8) NO se escribe antes de este punto (Principio III).

---

## Phase 7: DTO

**Nota de reconciliación**: `plan.md` había nombrado el DTO de consulta
`DeudaAcumuladaResponse`; se renombra aquí a `DeudaAcumuladaDTO` para seguir la
instrucción explícita de la ronda anterior de `/speckit.tasks`. El campo
`mes` de `AporteMensualRequest` se renombra a `mesHasta` (FR-012, resuelve U1).

- [x] T039 [P] [US1] Crear `AporteInicialRequest` (`BigDecimal monto`, `LocalDate fecha`) — `src/main/java/com/mibanquito/aportes/dto/AporteInicialRequest.java`
- [x] T040 [P] [US2] Crear `AporteMensualRequest` (`YearMonth mesHasta`, `BigDecimal montoPagado`, `LocalDate fecha`) — `src/main/java/com/mibanquito/aportes/dto/AporteMensualRequest.java`
- [x] T041 [P] [US3] Crear `DeudaAcumuladaDTO` (desglose de meses vencidos con su concepto — FR-005 —, multas, y total exacto) — `src/main/java/com/mibanquito/aportes/dto/DeudaAcumuladaDTO.java`

---

## Phase 8: Controlador Thymeleaf

**Nota de remediación (C1 de la 1ª ronda de análisis)**: la revisión original
no incluía ninguna tarea que restringiera las rutas por rol pese a que
`contracts/aportes-endpoints.md` ya lo exigía. T042–T044 ahora incluyen
explícitamente `@PreAuthorize`, y T046 agrega la prueba de integración que lo
verifica.

- [x] T042 [US1] Implementar en `AporteController` las rutas `GET`/`POST /socios/{socioId}/periodos/{periodoId}/aporte-inicial`, anotadas `@PreAuthorize("hasRole('ADMINISTRADOR')")` (FR-001, FR-011, FR-014; usa `AporteInicialRequest`) — `src/main/java/com/mibanquito/aportes/controller/AporteController.java` (depende de T033, T039)
- [x] T043 [US2] Implementar en `AporteController` las rutas `GET`/`POST /socios/{socioId}/periodos/{periodoId}/aporte-mensual`, anotadas `@PreAuthorize("hasRole('ADMINISTRADOR')")` (FR-002, FR-009, FR-010, FR-011, FR-012, FR-014; usa `AporteMensualRequest` con `mesHasta`) — mismo archivo (depende de T036, T040, T042)
- [x] T044 [US3] Implementar en `AporteController` la ruta `GET /socios/{socioId}/periodos/{periodoId}/deuda`, anotada `@PreAuthorize("hasRole('ADMINISTRADOR')")` para consulta de deuda/historial (FR-004, FR-014, SC-004; usa `DeudaAcumuladaDTO`) — mismo archivo (depende de T035, T041, T043)
- [x] T045 [P] Crear vistas Thymeleaf `aporte-inicial-form.html`, `aporte-mensual-form.html` (campo `mesHasta`, no `mes`), `deuda-socio.html` — `src/main/resources/templates/aportes/`
- [x] T046 Escribir `AporteControllerIT`: verifica que las 3 rutas devuelven 403/redirect para un usuario con rol `SOCIO` y responden correctamente para `ADMINISTRADOR` (FR-014, SC-005) — `src/test/java/com/mibanquito/aportes/controller/AporteControllerIT.java` (depende de T042, T043, T044)

**Nota de cierre (M3)**: la instrucción original de `/speckit.tasks` citaba
"RF-16" para la ruta de consulta de deuda/historial; `spec.md` solo define
FR-001–FR-014, no existe FR-016/RF-16. T044 queda definitivamente trazada
contra FR-004/SC-004 (no contra "RF-16").

---

## Phase 9: Verificación final

- [x] T047 Ejecutar `mvnw clean test` completo (todas las migraciones Flyway aplican desde cero, las 12 pruebas de `AporteServiceTest` y `AporteControllerIT` pasan) y confirmar `BUILD SUCCESS`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Migraciones (Fase 2)**: depende de Fase 1 — bloquea Entidades
- **Entidades (Fase 3)**: depende de Fase 2 — bloquea Repositorios
- **Repositorios (Fase 4)**: depende de Fase 3 — bloquea Tests
- **Tests de servicio (Fase 5)**: depende de Fase 4 — deben fallar antes de Fase 6
- **AporteService (Fase 6)**: depende de Fase 5 (pruebas ya escritas); T038 exige que las 12 pasen antes de continuar
- **DTO (Fase 7)**: depende de Fase 6 (usa tipos de `AporteService`/entidades)
- **Controlador (Fase 8)**: depende de Fase 6 completa (T038 en verde) y Fase 7 — NO se adelanta (Principio III)
- **Verificación final (Fase 9)**: depende de todas las anteriores

### Dentro de cada fase

- Fase 2: T003 y T004 en paralelo; T005–T008 secuenciales por sus FK
- Fase 3: T009, T010, T011 en paralelo; T012 espera a T009; T013 espera a T012
- Fase 4: T014–T017 en paralelo entre sí (cada uno depende solo de su entidad)
- Fase 5: secuenciales (mismo archivo `AporteServiceTest.java`)
- Fase 6: T030/T031/T032 en paralelo; T033→T034→T035→T036→T037 secuenciales (mismo archivo `AporteService.java`); T038 al final
- Fase 7: T039, T040, T041 en paralelo (archivos distintos)
- Fase 8: T042→T043→T044 secuenciales (mismo archivo `AporteController.java`); T045 en paralelo con ellas (vistas, archivos distintos); T046 depende de las 3 rutas completas

## Parallel Example: Fase 3 (Entidades)

```bash
# Lanzar juntas (no dependen entre sí):
Task: "Crear enum EstadoAporteMensual en src/main/java/com/mibanquito/aportes/entity/EstadoAporteMensual.java"
Task: "Crear entidad ParametroPeriodo en src/main/java/com/mibanquito/aportes/entity/ParametroPeriodo.java"
Task: "Crear entidad AporteInicial en src/main/java/com/mibanquito/aportes/entity/AporteInicial.java"

# Después de que termine EstadoAporteMensual:
Task: "Crear entidad AporteMensual en src/main/java/com/mibanquito/aportes/entity/AporteMensual.java"
```

## Implementation Strategy

Este feature se entrega como **un solo incremento** (no historia por historia),
porque `AporteService` es compartido por US1/US2/US3 y el test-first (Fase 5)
exige que los 12 tests existan y fallen antes de que exista cualquier
implementación de servicio — no es posible entregar US1 de forma aislada sin
antes tener toda la base de Fases 1–4.

1. Fases 1–4: infraestructura completa (Setup → Migraciones → Entidades → Repositorios).
2. Fase 5: escribir y confirmar que fallan las 12 pruebas de `AporteServiceTest`.
3. Fase 6: implementar `AporteService` hasta que las 12 pruebas pasen (T038).
4. Fase 7–8: DTO y controlador (con seguridad por rol), en ese orden, recién después de T038.
5. Fase 9: `mvnw clean test` completo → `BUILD SUCCESS`.

## Notes

- `[P]` = archivos distintos, sin dependencias pendientes
- `[USx]` = trazabilidad a la historia de usuario de `spec.md`; las fases de
  infraestructura compartida no llevan esta etiqueta
- Verificar que T018–T029 fallan antes de avanzar a la Fase 6 (test-first real,
  no solo nominal)
- Hallazgos de `/speckit.analyze` (2 rondas) resueltos en esta revisión:
  C1-ronda1 (T042–T044, T046), G1 (T017, T037), U1 (T021, T034, T036, T040),
  G2 (T026, T034), I1 (T024, T025, T032), M1 (T027), M2 (T019, T020, T041),
  M3 (nota de cierre en Fase 8), C1-ronda2/FR-006 (T029, T037),
  C2-ronda2/FR-008 (T028, T034). Hallazgos LOW no resueltos (fuera de alcance,
  no solicitados): Flyway ausente de "Primary Dependencies" en `plan.md`,
  posible efecto lateral de `GET /deuda`, y desincronización de `quickstart.md`
  con el nombre de campo `mesHasta`.
