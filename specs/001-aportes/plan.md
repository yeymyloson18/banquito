# Implementation Plan: Aportes

**Branch**: `001-aportes` | **Date**: 2026-07-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-aportes/spec.md`

## Summary

Registrar el aporte inicial libre de cada socio al ingresar a un periodo, registrar
sus aportes mensuales obligatorios, y calcular automáticamente la deuda acumulada
por morosidad (meses impagos al monto vigente de cada mes + una multa fija por mes
vencido), rechazando pagos parciales/sobrepagos, registros duplicados y cualquier
operación sobre un periodo cerrado. Se implementa como un monolito Spring Boot MVC
por capas (controller → service → repository → entidad JPA), con Thymeleaf para las
vistas del Administrador y MySQL 8 + Flyway como autoridad del esquema.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Security, Validation),
Spring Data JPA + Hibernate, Spring Security, Jakarta Bean Validation, Thymeleaf +
Bootstrap 5

**Storage**: MySQL 8, con Flyway como autoridad única del esquema (mandatado por la
constitución §3, aunque no fue listado explícitamente por el usuario en el stack de
persistencia)

**Testing**: JUnit 5 + Mockito (obligatorio para lógica financiera de morosidad y
cálculo de deuda, Principio III). Testcontainers no se incluye en este plan por no
haber sido solicitado; queda disponible como opción futura (constitución §3, ítem
opcional).

**Target Platform**: Servidor con JVM 21 (backend monolítico Spring Boot MVC),
renderizado server-side para navegador (Thymeleaf, mobile-first vía Bootstrap 5)

**Project Type**: Aplicación web monolítica de una sola capa desplegable (no hay
frontend/backend separados: Thymeleaf renderiza dentro del mismo proceso Spring
Boot), organizada por capas técnicas (Principio IV)

**Performance Goals**: No se definen objetivos de rendimiento específicos en el spec
(SC-001 a SC-004 son de corrección, no de throughput). Carga esperada baja — un
banco comunal típico opera con decenas de socios por periodo — por lo que se
prioriza corrección sobre optimización.

**Constraints**:
- Todo monto/tasa/saldo MUST ser `BigDecimal` (Principio VIII) — aplica a
  `AporteInicial.monto`, `AporteMensual.montoEsperado`, `MultaMora.monto`,
  `ParametroPeriodo.montoAporteMensual`, `ParametroPeriodo.montoMulta`.
- Todo aporte MUST asociarse a un periodo (Principio X, FR-001, FR-002).
- Ninguna operación de escritura MUST proceder sobre un periodo cerrado
  (Principio XIV, FR-011).
- Lógica de cálculo de morosidad y validación de pago exacto MUST desarrollarse
  test-first (Principio III).

**Scale/Scope**: 3 historias de usuario (aporte inicial, aporte mensual, cálculo de
morosidad), 11 requisitos funcionales, 4 entidades nuevas (`AporteInicial`,
`AporteMensual`, `MultaMora`, `ParametroPeriodo`) que referencian `Socio` y
`PeriodoAnual` de otros módulos (aún no implementados en este repo).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Nota |
|---|---|---|
| I. Especificación fuente de verdad | PASS | `spec.md` aprobado y clarificado (`/speckit-clarify`) antes de este plan |
| II. Reglas de negocio explícitas y verificables | PASS | FR-001..FR-011 son verificables; SC-001/SC-002 fijan casos exactos (S/105, S/160) para pruebas unitarias |
| III. Test-first para lógica de negocio | GATE → tasks.md | El cálculo de morosidad (FR-004) y la validación de pago exacto (FR-009) deben implementarse TDD: prueba falla → código mínimo → prueba pasa. Se marcará explícitamente en `tasks.md` |
| IV. Simplicidad sobre sofisticación | PASS | Monolito Spring Boot MVC por capas, sin patrones adicionales (no CQRS, no microservicios) |
| V. Trazabilidad completa | PASS → tasks.md | Cada tarea de `tasks.md` referenciará un FR-XXX de este spec |
| VI. Protección de datos personales | N/A | Este feature no introduce datos personales nuevos más allá de la FK a `Socio` (gestionada por el módulo Registro de socios) |
| VII. Constitución sin reglas volátiles | PASS | Montos de aporte y multa viven en `ParametroPeriodo` (spec.md), nunca hardcodeados |
| VIII. Dinero siempre `BigDecimal` | GATE → data-model.md | Todo campo monetario de las 4 entidades nuevas debe tipar `BigDecimal`; verificado en Phase 1 |
| IX. Seguridad no opcional | PASS | Spring Security ya en el stack; endpoints de registro restringidos a rol ADMINISTRADOR |
| X. Todo movimiento pertenece a un periodo | PASS | `AporteInicial` y `AporteMensual` llevan FK obligatoria a `PeriodoAnual` |
| XI. Nunca prestar más de lo que existe | N/A | Aplica al módulo de Préstamos, fuera de alcance de este feature |
| XII. Interés sobre saldo pendiente | N/A | Aplica al módulo de Préstamos, fuera de alcance de este feature |
| XIII. Distribución proporcional al capital | PASS | FR-006 asegura que las multas entran a la ganancia distribuible, sujeta a distribución proporcional al cierre (fuera de alcance de este feature, pero no lo contradice) |
| XIV. Periodo cerrado inmutable | PASS | FR-011 implementa este principio directamente |
| XV. No cerrar con préstamos pendientes | N/A | Aplica al módulo de Cierre anual, fuera de alcance de este feature |
| XVI. Morosidad acumulativa mes a mes | PASS | FR-004 y los casos S/105/S/160 (SC-001, SC-002) son la aplicación directa de este principio |

**Resultado**: PASS. No se requieren entradas en Complexity Tracking — no hay
violaciones que justificar.

**Re-chequeo post Phase 1 (diseño completo)**: PASS confirmado.
- Principio VIII (BigDecimal): verificado — las 4 entidades nuevas en
  `data-model.md` tipan todo campo monetario como `BigDecimal(10,2)`.
- Principio III (test-first): pendiente de ejecución, no de diseño — quedará
  como tarea explícita en `tasks.md` (pruebas de `MorosidadServiceTest` antes
  de la implementación).
- Principio V (trazabilidad): `quickstart.md` mapea cada escenario 1:1 a un
  Acceptance Scenario y a un SC-XXX del spec; `tasks.md` deberá mapear cada
  tarea a un FR-XXX.
- Principio XIV (periodo cerrado inmutable): verificado — `data-model.md`
  documenta la validación de escritura contra periodo cerrado en
  `AporteInicial` y `AporteMensual`; `research.md` §4 centraliza el mecanismo.
- Ningún gate quedó abierto sin resolución de diseño.

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

Este es el primer feature implementado en el repositorio: no existe aún un
proyecto Maven. Este plan establece la estructura base del monolito Spring Boot
MVC por capas (Principio IV) que usarán también los siguientes módulos.

```text
pom.xml

src/main/java/com/mibanquito/
├── model/
│   ├── AporteInicial.java
│   ├── AporteMensual.java
│   ├── EstadoAporteMensual.java   # enum PENDIENTE/PAGADO
│   ├── MultaMora.java
│   └── ParametroPeriodo.java
├── repository/
│   ├── AporteInicialRepository.java
│   ├── AporteMensualRepository.java
│   ├── MultaMoraRepository.java
│   └── ParametroPeriodoRepository.java
├── service/
│   ├── AporteInicialService.java
│   ├── AporteMensualService.java
│   └── MorosidadService.java       # cálculo de deuda acumulada (FR-004)
├── controller/
│   └── AporteController.java
└── validation/
    └── MontoPositivoValidator.java # o equivalente Jakarta Bean Validation

src/main/resources/
├── templates/aportes/
│   ├── aporte-inicial-form.html
│   ├── aporte-mensual-form.html
│   └── deuda-socio.html
└── db/migration/
    ├── V1__crear_aporte_inicial.sql
    ├── V2__crear_parametro_periodo.sql
    ├── V3__crear_aporte_mensual.sql
    └── V4__crear_multa_mora.sql

src/test/java/com/mibanquito/
├── unit/
│   ├── MorosidadServiceTest.java   # casos S/105 y S/160 (SC-001, SC-002)
│   ├── AporteInicialServiceTest.java
│   └── AporteMensualServiceTest.java
└── integration/
    └── AporteControllerIT.java
```

**Structure Decision**: Proyecto Maven único (`com.mibanquito`), organizado por
capas técnicas (`model` / `repository` / `service` / `controller`) y no por
módulo de negocio, conforme al Principio IV (monolito simple). `Socio` y
`PeriodoAnual` se referencian por FK/ID pero sus entidades serán creadas por los
módulos "Registro de socios" y "Apertura de periodo anual" respectivamente — no
se redefinen aquí para evitar duplicar responsabilidad de otro feature.

## Complexity Tracking

*Sin violaciones que justificar — tabla vacía intencionalmente.*
