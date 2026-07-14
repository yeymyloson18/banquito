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

**Nota de alcance añadida**: `spec.md` declara como dependencia "Registro de
socios" y "Apertura de periodo anual", pero ninguno de esos módulos existe aún
en el repo. Sin tablas `socio` y `periodo_anual`, las FK reales de este feature
no podrían crearse y `mvnw clean test` fallaría. Se añaden 2 migraciones stub
mínimas (T003, T004) para desbloquear esto; los módulos reales deberán
`ALTER TABLE` estas tablas al implementarse, sin tocar las FK ya creadas por
Aportes.

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

- [ ] T001 Crear `pom.xml` con las dependencias del stack obligatorio: Spring Boot 3 Starter Web, Data JPA, Security, Validation; MySQL Connector/J; Flyway (`flyway-core` + `flyway-mysql`); Thymeleaf; JUnit 5; Mockito — `pom.xml`
- [ ] T002 Configurar `src/main/resources/application.yml` con datasource MySQL 8 y `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration` — `src/main/resources/application.yml`

**Checkpoint**: proyecto compila (`mvnw compile`) con el stack correcto configurado.

---

## Phase 2: Migraciones Flyway (Foundational)

**Purpose**: Esquema de base de datos para las 4 tablas de Aportes y sus FK

**⚠️ CRITICAL**: Ninguna tarea de Entidades/Repositorios puede empezar hasta que estas migraciones existan

- [ ] T003 [P] Crear migración stub de `periodo_anual` (`id BIGINT PK`, `estado VARCHAR(10)` con valores `ABIERTO`/`CERRADO`) — `src/main/resources/db/migration/V1__crear_periodo_anual_stub.sql`
- [ ] T004 [P] Crear migración stub de `socio` (`id BIGINT PK`) — `src/main/resources/db/migration/V2__crear_socio_stub.sql`
- [ ] T005 Crear migración de `parametro_periodo` (`id`, `periodo_id BIGINT FK→periodo_anual`, `monto_aporte_mensual DECIMAL(10,2)`, `monto_multa DECIMAL(10,2)`, `vigente_desde DATE`) — `src/main/resources/db/migration/V3__crear_parametro_periodo.sql` (depende de T003)
- [ ] T006 Crear migración de `aporte_inicial` (`id`, `socio_id BIGINT FK→socio`, `periodo_id BIGINT FK→periodo_anual`, `monto DECIMAL(10,2)`, `fecha DATE`) — `src/main/resources/db/migration/V4__crear_aporte_inicial.sql` (depende de T003, T004)
- [ ] T007 Crear migración de `aporte_mensual` (`id`, `socio_id BIGINT FK→socio`, `periodo_id BIGINT FK→periodo_anual`, `mes DATE` o `CHAR(7)`, `monto_esperado DECIMAL(10,2)`, `fecha_pago DATE NULL`, `estado VARCHAR(10)`, `UNIQUE (socio_id, periodo_id, mes)` — FR-010) — `src/main/resources/db/migration/V5__crear_aporte_mensual.sql` (depende de T003, T004)
- [ ] T008 Crear migración de `multa_mora` (`id`, `aporte_mensual_id BIGINT FK→aporte_mensual UNIQUE`, `monto DECIMAL(10,2)`, `mes_generada DATE` o `CHAR(7)`) — `src/main/resources/db/migration/V6__crear_multa_mora.sql` (depende de T007)

**Checkpoint**: `mvnw flyway:migrate` (o el arranque de la app) aplica las 6 migraciones sin error.

---

## Phase 3: Entidades JPA (Foundational)

**Purpose**: Mapeo objeto-relacional de las 4 tablas nuevas

- [ ] T009 [P] Crear enum `EstadoAporteMensual` (`PENDIENTE`, `PAGADO`) — `src/main/java/com/mibanquito/aportes/entity/EstadoAporteMensual.java`
- [ ] T010 [P] Crear entidad `ParametroPeriodo` (`BigDecimal montoAporteMensual`, `BigDecimal montoMulta`, `LocalDate vigenteDesde`, FK `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/ParametroPeriodo.java`
- [ ] T011 [P] Crear entidad `AporteInicial` (`BigDecimal monto`, `LocalDate fecha`, FK `socioId`, `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/AporteInicial.java`
- [ ] T012 [US2] Crear entidad `AporteMensual` (`YearMonth mes`, `BigDecimal montoEsperado`, `LocalDate fechaPago`, `EstadoAporteMensual estado`, FK `socioId`, `periodoId`) — `src/main/java/com/mibanquito/aportes/entity/AporteMensual.java` (depende de T009 por el enum)
- [ ] T013 [US3] Crear entidad `MultaMora` (`BigDecimal monto`, `YearMonth mesGenerada`, FK única `aporteMensualId`) — `src/main/java/com/mibanquito/aportes/entity/MultaMora.java` (depende de T012)

**Checkpoint**: las 5 clases compilan y mapean 1:1 a las tablas de la Fase 2.

---

## Phase 4: Repositorios (Foundational)

**Purpose**: Acceso a datos Spring Data JPA, sin lógica de negocio

- [ ] T014 [P] Crear `ParametroPeriodoRepository extends JpaRepository<ParametroPeriodo, Long>` — `src/main/java/com/mibanquito/aportes/repository/ParametroPeriodoRepository.java` (depende de T010)
- [ ] T015 [P] [US1] Crear `AporteInicialRepository extends JpaRepository<AporteInicial, Long>` — `src/main/java/com/mibanquito/aportes/repository/AporteInicialRepository.java` (depende de T011)
- [ ] T016 [P] [US2] Crear `AporteMensualRepository extends JpaRepository<AporteMensual, Long>` con `findBySocioIdAndPeriodoId(...)` y `findBySocioIdAndPeriodoIdAndMes(...)` para soportar `calcularDeudaAcumulada` — `src/main/java/com/mibanquito/aportes/repository/AporteMensualRepository.java` (depende de T012)
- [ ] T017 [P] [US3] Crear `MultaMoraRepository extends JpaRepository<MultaMora, Long>` con `findByAporteMensualIdIn(...)` — `src/main/java/com/mibanquito/aportes/repository/MultaMoraRepository.java` (depende de T013)

**Checkpoint**: Fundación completa — Fase 5 (tests) puede empezar.

---

## Phase 5: Tests de `AporteService` (deben fallar primero — Principio III)

**Purpose**: Verificación ejecutable de SC-001/SC-002 y de las reglas de rechazo, escrita ANTES de que exista `AporteService`

**⚠️ CRITICAL**: Estas 5 pruebas deben compilar contra un `AporteService` aún no implementado (o vacío) y **fallar** antes de la Fase 6. Todas viven en el mismo archivo, por lo que no son paralelizables entre sí.

- [ ] T018 [US3] Test: socio sin meses vencidos — `calcularDeudaAcumulada` devuelve solo el aporte del mes actual — `src/test/java/com/mibanquito/aportes/service/AporteServiceTest.java` (método `calcularDeudaAcumulada_sinMora_devuelveSoloMesActual`)
- [ ] T019 [US3] Test: 1 mes vencido (enero, S/50) pagado en febrero (S/50) + 1 multa (S/5) = **S/105 exacto** (SC-001) — mismo archivo (método `calcularDeudaAcumulada_unMesVencido_totalCientoCinco`)
- [ ] T020 [US3] Test: 2 meses vencidos (enero y febrero, S/50 c/u) pagados en marzo (S/50) + 2 multas (S/5 c/u) = **S/160 exacto** (SC-002) — mismo archivo (método `calcularDeudaAcumulada_dosMesesVencidos_totalCientoSesenta`)
- [ ] T021 [US3] Test: socio en mora, `registrarAporteMensual` con monto que no coincide exactamente con la deuda acumulada lanza `PagoParcialNoPermitidoException` (FR-009) — mismo archivo (método `registrarAporteMensual_pagoParcialEnMora_lanzaExcepcion`)
- [ ] T022 [US1] Test: `registrarAporteInicial` con `monto <= 0` falla la validación (FR-001) — mismo archivo (método `registrarAporteInicial_montoNoPositivo_lanzaExcepcion`)

**Checkpoint**: `mvnw test -Dtest=AporteServiceTest` — las 5 pruebas existen y **fallan** (no hay implementación aún).

---

## Phase 6: Implementar `AporteService` (hasta que las 5 pruebas de la Fase 5 pasen)

- [ ] T023 [P] [US2] Crear excepción `PagoParcialNoPermitidoException` (RuntimeException) — `src/main/java/com/mibanquito/aportes/service/PagoParcialNoPermitidoException.java`
- [ ] T024 [P] Crear excepción `PeriodoCerradoException` (RuntimeException) — `src/main/java/com/mibanquito/aportes/service/PeriodoCerradoException.java`
- [ ] T025 [US1] Implementar `AporteService.registrarAporteInicial` (valida `monto > 0`, rechaza si periodo `CERRADO` vía `PeriodoCerradoException`, persiste `AporteInicial`) — `src/main/java/com/mibanquito/aportes/service/AporteService.java` (depende de T015, T023, T024)
- [ ] T026 [US3] Implementar `AporteService.calcularDeudaAcumulada` (suma `montoEsperado` de `AporteMensual` PENDIENTE con mes <= mes actual + `MultaMora` asociadas) — mismo archivo (depende de T016, T017, T025)
- [ ] T027 [US2] Implementar `AporteService.registrarAporteMensual` (rechaza duplicado `PAGADO` — FR-010; rechaza si periodo `CERRADO` — FR-011; usa `calcularDeudaAcumulada` y rechaza con `PagoParcialNoPermitidoException` si el monto no coincide exacto estando en mora — FR-009) — mismo archivo (depende de T026)
- [ ] T028 Ejecutar `mvnw test -Dtest=AporteServiceTest` y confirmar que las 5 pruebas (T018–T022) **pasan** — checkpoint de verificación, sin archivo nuevo

**Checkpoint**: `AporteService` completo y probado. El `AporteController` (Fase 8) NO se escribe antes de este punto (Principio III).

---

## Phase 7: DTO

**Nota de reconciliación**: `plan.md` había nombrado el DTO de consulta
`DeudaAcumuladaResponse`; se renombra aquí a `DeudaAcumuladaDTO` para seguir la
instrucción explícita de esta ronda de `/speckit.tasks`.

- [ ] T029 [P] [US1] Crear `AporteInicialRequest` (`BigDecimal monto`, `LocalDate fecha`) — `src/main/java/com/mibanquito/aportes/dto/AporteInicialRequest.java`
- [ ] T030 [P] [US2] Crear `AporteMensualRequest` (`YearMonth mes`, `BigDecimal montoPagado`, `LocalDate fecha`) — `src/main/java/com/mibanquito/aportes/dto/AporteMensualRequest.java`
- [ ] T031 [P] [US3] Crear `DeudaAcumuladaDTO` (desglose de meses vencidos, multas, y total exacto) — `src/main/java/com/mibanquito/aportes/dto/DeudaAcumuladaDTO.java`

---

## Phase 8: Controlador Thymeleaf

- [ ] T032 [US1] Implementar en `AporteController` las rutas `GET`/`POST /socios/{socioId}/periodos/{periodoId}/aporte-inicial` (FR-001, FR-011; usa `AporteInicialRequest`) — `src/main/java/com/mibanquito/aportes/controller/AporteController.java` (depende de T025, T029)
- [ ] T033 [US2] Implementar en `AporteController` las rutas `GET`/`POST /socios/{socioId}/periodos/{periodoId}/aporte-mensual` (FR-002, FR-009, FR-010, FR-011; usa `AporteMensualRequest`) — mismo archivo (depende de T027, T030, T032)
- [ ] T034 [US3] Implementar en `AporteController` la ruta `GET /socios/{socioId}/periodos/{periodoId}/deuda` para consulta de deuda/historial (FR-004, SC-004; usa `DeudaAcumuladaDTO`) — mismo archivo (depende de T026, T031, T033)
- [ ] T035 [P] Crear vistas Thymeleaf `aporte-inicial-form.html`, `aporte-mensual-form.html`, `deuda-socio.html` — `src/main/resources/templates/aportes/`

**Nota**: la instrucción original citaba "RF-16" para la ruta de consulta de
deuda/historial; `spec.md` solo define FR-001–FR-011, no existe FR-016/RF-16.
T034 se referencia contra FR-004 y SC-004, que son los requisitos reales que
cubren esa consulta — confirmar con el usuario si "RF-16" refería a otro
documento no compartido aún.

---

## Phase 9: Verificación final

- [ ] T036 Ejecutar `mvnw clean test` completo (todas las migraciones Flyway aplican desde cero, las 5 pruebas de `AporteServiceTest` y cualquier prueba de integración del controlador pasan) y confirmar `BUILD SUCCESS`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Migraciones (Fase 2)**: depende de Fase 1 — bloquea Entidades
- **Entidades (Fase 3)**: depende de Fase 2 — bloquea Repositorios
- **Repositorios (Fase 4)**: depende de Fase 3 — bloquea Tests
- **Tests de servicio (Fase 5)**: depende de Fase 4 — deben fallar antes de Fase 6
- **AporteService (Fase 6)**: depende de Fase 5 (pruebas ya escritas); T028 exige que pasen antes de continuar
- **DTO (Fase 7)**: depende de Fase 6 (usa tipos de `AporteService`/entidades)
- **Controlador (Fase 8)**: depende de Fase 6 completa (T028 en verde) y Fase 7 — NO se adelanta (Principio III)
- **Verificación final (Fase 9)**: depende de todas las anteriores

### Dentro de cada fase

- Fase 2: T003 y T004 en paralelo; T005–T008 secuenciales por sus FK
- Fase 3: T009, T010, T011 en paralelo; T012 espera a T009; T013 espera a T012
- Fase 4: T014–T017 en paralelo entre sí (cada uno depende solo de su entidad)
- Fase 5: secuenciales (mismo archivo `AporteServiceTest.java`)
- Fase 6: T023/T024 en paralelo; T025→T026→T027 secuenciales (mismo archivo `AporteService.java`); T028 al final
- Fase 7: T029, T030, T031 en paralelo (archivos distintos)
- Fase 8: T032→T033→T034 secuenciales (mismo archivo `AporteController.java`); T035 en paralelo con ellas (vistas, archivos distintos)

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
exige que los 5 tests existan y fallen antes de que exista cualquier
implementación de servicio — no es posible entregar US1 de forma aislada sin
antes tener toda la base de Fases 1–4.

1. Fases 1–4: infraestructura completa (Setup → Migraciones → Entidades → Repositorios).
2. Fase 5: escribir y confirmar que fallan las 5 pruebas de `AporteServiceTest`.
3. Fase 6: implementar `AporteService` hasta que las 5 pruebas pasen (T028).
4. Fase 7–8: DTO y controlador, en ese orden, recién después de T028.
5. Fase 9: `mvnw clean test` completo → `BUILD SUCCESS`.

## Notes

- `[P]` = archivos distintos, sin dependencias pendientes
- `[USx]` = trazabilidad a la historia de usuario de `spec.md`; las fases de
  infraestructura compartida no llevan esta etiqueta
- Verificar que T018–T022 fallan antes de avanzar a la Fase 6 (test-first real,
  no solo nominal)
- 2 discrepancias detectadas contra la instrucción original del usuario y
  resueltas explícitamente en este archivo: el DTO se nombra
  `DeudaAcumuladaDTO` (Fase 7) y la ruta de consulta de deuda se traza contra
  FR-004/SC-004 en vez de la inexistente "RF-16" (Fase 8)
