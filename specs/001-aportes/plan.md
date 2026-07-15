# Implementation Plan: Aportes

**Branch**: `001-aportes` | **Date**: 2026-07-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-aportes/spec.md`

**Fuentes obligatorias leídas antes de este plan**: `.specify/memory/constitution.md`,
`docs/arquitectura-general.md`, `specs/001-aportes/spec.md`. Este plan no introduce
tecnología ni estructura de paquetes distinta a la ya definida en esas dos fuentes.

## Summary

Registrar el aporte inicial libre de cada socio al ingresar a un periodo, registrar
sus aportes mensuales obligatorios, y calcular automáticamente la deuda acumulada
por morosidad (meses impagos al monto histórico de cada uno + el mes actual + una
multa por cada mes vencido), rechazando pagos parciales, registros duplicados y
cualquier operación sobre un periodo cerrado. Implementado como monolito Spring Boot
MVC por capas (`Controller → Service → Repository → Entity`) bajo el paquete
`com.mibanquito.aportes`, siguiendo la convención de `docs/arquitectura-general.md`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3 (Web, Data JPA, Security, Validation),
Thymeleaf + Bootstrap 5

**Storage**: MySQL 8, con Flyway como autoridad única del esquema

**Testing**: JUnit 5 + Mockito — obligatorio y **test-first** para la lógica de
`AporteService` (Principio III de la constitución): las pruebas unitarias del
servicio deben existir y pasar antes de escribir el `AporteController`.

**Target Platform**: Servidor con JVM 21 (monolito Spring Boot MVC), vistas
server-rendered para navegador (Thymeleaf + Bootstrap 5, mobile-first)

**Project Type**: Aplicación web monolítica de un solo proyecto Maven, organizada
por capas dentro de un paquete por módulo (`docs/arquitectura-general.md`), no hay
frontend/backend separados

**Performance Goals**: No definidos en el spec (SC-001..SC-004 son de corrección,
no de throughput); carga esperada baja (banco comunal, decenas de socios)

**Constraints**:
- Todo monto/tasa/saldo MUST ser `BigDecimal` (Principio VIII).
- `AporteMensual.montoEsperado` y la `MultaMora.monto` MUST guardarse de forma
  histórica en el momento en que el mes vence — **nunca se recalculan** si
  `ParametroPeriodo` cambia después (FR-008, instrucción explícita del usuario).
- `registrarAporteMensual` MUST rechazar pago parcial cuando el socio está en
  mora, lanzando `PagoParcialNoPermitidoException` (FR-009).
- Ninguna operación de escritura MUST proceder sobre un periodo cerrado
  (Principio XIV, FR-011).
- `registrarAporteMensual` MUST recibir un parámetro `mesHasta` y saldar en
  una sola llamada todos los `AporteMensual` PENDIENTE con mes `<= mesHasta`,
  generándolos de forma perezosa si no existen aún (FR-012).
- El mes de ingreso de un socio a un periodo se deriva de `AporteInicial.fecha`;
  ningún `AporteMensual` se genera antes de ese mes (FR-007, FR-013).
- Toda ruta de `AporteController` MUST estar anotada con
  `@PreAuthorize("hasRole('ADMINISTRADOR')")` (Principio IX, FR-014); se
  asume que la configuración base de Spring Security
  (`@EnableMethodSecurity`, login, roles) la provee el módulo Autenticación
  (fuera de alcance de este feature).
- El `AporteController` NO se implementa hasta que las pruebas unitarias de
  `AporteService` existan y pasen (Principio III, test-first).

**Scale/Scope**: 3 historias de usuario, 14 requisitos funcionales (FR-001 a
FR-014), 4 entidades nuevas bajo `com.mibanquito.aportes.entity`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Nota |
|---|---|---|
| I. Especificación fuente de verdad | PASS | `spec.md` aprobado y clarificado antes de este plan |
| II. Reglas de negocio explícitas y verificables | PASS | FR-001..FR-014 verificables; SC-001/SC-002 fijan los casos S/105 y S/160 como pruebas exactas |
| III. Test-first para lógica de negocio | GATE → tasks.md | `AporteServiceTest` (10 casos, incluyendo S/105, S/160, rechazo de pago parcial, duplicado, periodo cerrado, mes de ingreso y parametrización) debe escribirse y fallar antes de implementar `AporteService`; `AporteController` no se escribe hasta que esas pruebas pasen |
| IV. Simplicidad sobre sofisticación | PASS | Monolito Spring Boot MVC por capas, sin patrones adicionales, conforme a `docs/arquitectura-general.md` |
| V. Trazabilidad completa | PASS → tasks.md | Cada tarea de `tasks.md` referenciará un FR-XXX |
| VI. Protección de datos personales | N/A | Sin datos personales nuevos más allá de la FK a `Socio` |
| VII. Constitución sin reglas volátiles | PASS | Montos de aporte y multa viven en `ParametroPeriodo`, nunca hardcodeados; verificado por test dedicado (FR-003) |
| VIII. Dinero siempre `BigDecimal` | GATE → data-model.md | Verificado: las 4 entidades tipan todo campo monetario como `BigDecimal` |
| IX. Seguridad no opcional | PASS | `AporteController` anota cada ruta con `@PreAuthorize("hasRole('ADMINISTRADOR')")` (FR-014); probado por `AporteControllerIT` |
| X. Todo movimiento pertenece a un periodo | PASS | `AporteInicial` y `AporteMensual` llevan FK obligatoria a `periodo_anual` |
| XI. Nunca prestar más de lo que existe | N/A | Módulo de Préstamos, fuera de alcance |
| XII. Interés sobre saldo pendiente | N/A | Módulo de Préstamos, fuera de alcance |
| XIII. Distribución proporcional al capital | PASS | FR-006: Aportes persiste y expone el agregado de multas por periodo; la distribución real la ejecuta el módulo Cierre anual (fuera de alcance) |
| XIV. Periodo cerrado inmutable | PASS | FR-011: rechazo explícito de escritura sobre periodo cerrado |
| XV. No cerrar con préstamos pendientes | N/A | Módulo de Cierre anual, fuera de alcance |
| XVI. Morosidad acumulativa mes a mes | PASS | `calcularDeudaAcumulada` + casos S/105/S/160 son la aplicación directa de este principio |

**Resultado**: PASS. Sin entradas en Complexity Tracking.

**Re-chequeo post Phase 1 (diseño completo)**: PASS confirmado.
- BigDecimal (Principio VIII): confirmado en `data-model.md` para las 4 entidades.
- Historial inmutable de `montoEsperado`/`MultaMora.monto` (FR-008): confirmado
  en `data-model.md` — se fija en el momento de vencer el mes, no se recalcula.
- Test-first (Principio III): `tasks.md` secuencia `AporteServiceTest` (10
  casos, incluyendo FR-010 y FR-011) → `AporteService` → `AporteController`.
- Periodo cerrado inmutable (Principio XIV / FR-011): confirmado en
  `data-model.md` y `research.md` §4.
- Pago consolidado multi-mes (FR-012): resuelto en `data-model.md`/`research.md`
  §8 — `registrarAporteMensual(socioId, periodoId, mesHasta, montoPagado, fecha)`.
- Mes de ingreso (FR-007/FR-013): resuelto en `data-model.md`/`research.md` §10
  — derivado de `AporteInicial.fecha`, sin campo nuevo en `Socio`.
- Integración con Caja (FR-006): resuelto en `data-model.md`/`research.md` §9
  — Aportes expone un agregado consultable, no gestiona el saldo de caja.
- Seguridad por rol (FR-014): resuelto en `research.md` §11 — `@PreAuthorize`
  a nivel de `AporteController`, probado por `AporteControllerIT`.

**Re-análisis tras `/speckit.analyze` (remediación)**: los 3 hallazgos CRITICAL
(C1 seguridad, G1 multas→caja, U1 ambigüedad de pago multi-mes) y los 2 HIGH
(G2 mes de ingreso, I1 test-first de FR-010/FR-011) quedaron resueltos en
`spec.md` (FR-012–FR-014, SC-005), `data-model.md` (§ nuevas), `research.md`
(§7–§11), `contracts/aportes-endpoints.md` y `tasks.md` (10 tests en Fase 5,
tareas nuevas en Fases 4/6/8). Los MEDIUM (M1 parametrización, M2 detalle de
concepto, M3 "RF-16") también quedaron resueltos — ver notas en `tasks.md`.

## Project Structure

### Documentation (this feature)

```text
specs/001-aportes/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Primer feature implementado en el repositorio: no existe aún proyecto Maven. Se
sigue estrictamente la convención de `docs/arquitectura-general.md` (paquete por
módulo, subpaquetes por capa técnica).

```text
pom.xml

src/main/java/com/mibanquito/aportes/
├── entity/
│   ├── AporteInicial.java
│   ├── AporteMensual.java
│   ├── EstadoAporteMensual.java        # enum PENDIENTE/PAGADO
│   ├── MultaMora.java
│   └── ParametroPeriodo.java
├── repository/
│   ├── AporteInicialRepository.java
│   ├── AporteMensualRepository.java
│   ├── MultaMoraRepository.java         # incluye sumMontoByPeriodoId (FR-006)
│   └── ParametroPeriodoRepository.java
├── service/
│   ├── AporteService.java              # registrarAporteInicial, registrarAporteMensual(mesHasta), calcularDeudaAcumulada, obtenerTotalMultasPeriodo
│   ├── PagoParcialNoPermitidoException.java
│   ├── PeriodoCerradoException.java
│   └── AporteMensualYaPagadoException.java   # FR-010, ver tasks.md T030
├── dto/
│   ├── AporteInicialRequest.java
│   ├── AporteMensualRequest.java        # campo mesHasta (no "mes"), ver FR-012
│   └── DeudaAcumuladaDTO.java           # ver tasks.md Fase 7, nombre alineado con /speckit.tasks
└── controller/
    └── AporteController.java           # rutas anotadas @PreAuthorize("hasRole('ADMINISTRADOR')") (FR-014); se implementa DESPUÉS de que AporteServiceTest pase

src/main/resources/
├── templates/aportes/
│   ├── aporte-inicial-form.html
│   ├── aporte-mensual-form.html
│   └── deuda-socio.html
└── db/migration/
    ├── V1__crear_periodo_anual_stub.sql   # stub mínimo, ver tasks.md T003
    ├── V2__crear_socio_stub.sql           # stub mínimo, ver tasks.md T004
    ├── V3__crear_parametro_periodo.sql
    ├── V4__crear_aporte_inicial.sql
    ├── V5__crear_aporte_mensual.sql
    └── V6__crear_multa_mora.sql

src/test/java/com/mibanquito/aportes/
├── service/
│   └── AporteServiceTest.java          # 10 casos: S/105, S/160, pago multi-mes, pago parcial, duplicado, periodo cerrado, mes de ingreso, parametrización — PRIMERO
└── controller/
    └── AporteControllerIT.java         # incluye verificación de rol ADMINISTRADOR vs SOCIO (FR-014)
```

**Structure Decision**: Paquete `com.mibanquito.aportes` con subpaquetes
`entity`/`repository`/`service`/`dto`/`controller`, exactamente como especifica
`docs/arquitectura-general.md`. `Socio` y `PeriodoAnual` (y su tabla
`periodo_anual`) son propiedad de otros módulos: se referencian por FK/tipo pero
no se redefinen ni gestionan aquí.

## Complexity Tracking

*Sin violaciones que justificar — tabla vacía intencionalmente.*
