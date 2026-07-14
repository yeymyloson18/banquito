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
- El `AporteController` NO se implementa hasta que las pruebas unitarias de
  `AporteService` existan y pasen (Principio III, test-first).

**Scale/Scope**: 3 historias de usuario, 11 requisitos funcionales (FR-001 a
FR-011), 4 entidades nuevas bajo `com.mibanquito.aportes.entity`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Nota |
|---|---|---|
| I. Especificación fuente de verdad | PASS | `spec.md` aprobado y clarificado antes de este plan |
| II. Reglas de negocio explícitas y verificables | PASS | FR-001..FR-011 verificables; SC-001/SC-002 fijan los casos S/105 y S/160 como pruebas exactas |
| III. Test-first para lógica de negocio | GATE → tasks.md | `AporteServiceTest` (S/105, S/160, rechazo de pago parcial) debe escribirse y fallar antes de implementar `AporteService`; `AporteController` no se escribe hasta que esas pruebas pasen |
| IV. Simplicidad sobre sofisticación | PASS | Monolito Spring Boot MVC por capas, sin patrones adicionales, conforme a `docs/arquitectura-general.md` |
| V. Trazabilidad completa | PASS → tasks.md | Cada tarea de `tasks.md` referenciará un FR-XXX |
| VI. Protección de datos personales | N/A | Sin datos personales nuevos más allá de la FK a `Socio` |
| VII. Constitución sin reglas volátiles | PASS | Montos de aporte y multa viven en `ParametroPeriodo`, nunca hardcodeados |
| VIII. Dinero siempre `BigDecimal` | GATE → data-model.md | Verificado: las 4 entidades tipan todo campo monetario como `BigDecimal` |
| IX. Seguridad no opcional | PASS | Spring Security con control de acceso por rol a nivel de `AporteController`, conforme a `docs/arquitectura-general.md` §Seguridad |
| X. Todo movimiento pertenece a un periodo | PASS | `AporteInicial` y `AporteMensual` llevan FK obligatoria a `periodo_anual` |
| XI. Nunca prestar más de lo que existe | N/A | Módulo de Préstamos, fuera de alcance |
| XII. Interés sobre saldo pendiente | N/A | Módulo de Préstamos, fuera de alcance |
| XIII. Distribución proporcional al capital | PASS | FR-006: multas entran a ganancia distribuible |
| XIV. Periodo cerrado inmutable | PASS | FR-011: rechazo explícito de escritura sobre periodo cerrado |
| XV. No cerrar con préstamos pendientes | N/A | Módulo de Cierre anual, fuera de alcance |
| XVI. Morosidad acumulativa mes a mes | PASS | `calcularDeudaAcumulada` + casos S/105/S/160 son la aplicación directa de este principio |

**Resultado**: PASS. Sin entradas en Complexity Tracking.

**Re-chequeo post Phase 1 (diseño completo)**: PASS confirmado.
- BigDecimal (Principio VIII): confirmado en `data-model.md` para las 4 entidades.
- Historial inmutable de `montoEsperado`/`MultaMora.monto` (FR-008): confirmado
  en `data-model.md` — se fija en el momento de vencer el mes, no se recalcula.
- Test-first (Principio III): `tasks.md` deberá secuenciar
  `AporteServiceTest` → `AporteService` → `AporteController` en ese orden estricto.
- Periodo cerrado inmutable (Principio XIV / FR-011): confirmado en
  `data-model.md` y `research.md` §4.

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
│   ├── MultaMoraRepository.java
│   └── ParametroPeriodoRepository.java
├── service/
│   ├── AporteService.java              # registrarAporteInicial, registrarAporteMensual, calcularDeudaAcumulada
│   └── PagoParcialNoPermitidoException.java
├── dto/
│   ├── AporteInicialRequest.java
│   ├── AporteMensualRequest.java
│   └── DeudaAcumuladaResponse.java
└── controller/
    └── AporteController.java           # se implementa DESPUÉS de que AporteServiceTest pase

src/main/resources/
├── templates/aportes/
│   ├── aporte-inicial-form.html
│   ├── aporte-mensual-form.html
│   └── deuda-socio.html
└── db/migration/
    ├── V1__crear_parametro_periodo.sql
    ├── V2__crear_aporte_inicial.sql
    ├── V3__crear_aporte_mensual.sql
    └── V4__crear_multa_mora.sql

src/test/java/com/mibanquito/aportes/
├── service/
│   └── AporteServiceTest.java          # casos S/105, S/160, y rechazo de pago parcial — PRIMERO
└── controller/
    └── AporteControllerIT.java
```

**Structure Decision**: Paquete `com.mibanquito.aportes` con subpaquetes
`entity`/`repository`/`service`/`dto`/`controller`, exactamente como especifica
`docs/arquitectura-general.md`. `Socio` y `PeriodoAnual` (y su tabla
`periodo_anual`) son propiedad de otros módulos: se referencian por FK/tipo pero
no se redefinen ni gestionan aquí.

## Complexity Tracking

*Sin violaciones que justificar — tabla vacía intencionalmente.*
