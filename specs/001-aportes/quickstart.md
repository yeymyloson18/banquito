# Quickstart: Validar el feature Aportes

Guía para ejecutar y verificar manualmente que el feature cumple sus historias de
usuario y criterios de éxito (SC-001 a SC-004). No sustituye a las pruebas
unitarias descritas en `data-model.md`/`research.md` §5.

## Prerrequisitos

1. MySQL 8 corriendo localmente (o vía Docker) con el esquema aplicado por
   Flyway (`src/main/resources/db/migration/V1__...` a `V4__...`, ver
   `plan.md` → Project Structure).
2. Un `PeriodoAnual` existente con `estado = ABIERTO`.
3. Un `Socio` existente asociado a ese periodo (módulo Registro de socios).
4. Un `ParametroPeriodo` para ese periodo con, por ejemplo, `montoAporteMensual
   = 50.00` y `montoMulta = 5.00`, `vigenteDesde` = inicio del periodo.
5. Aplicación levantada: `mvn spring-boot:run`, autenticado como usuario con rol
   ADMINISTRADOR (ver contracts/aportes-endpoints.md).

## Escenario 1 — Aporte inicial (US1)

1. `POST /socios/{socioId}/periodos/{periodoId}/aporte-inicial` con
   `monto=100.00`, `fecha=2026-01-05`.
2. **Esperado**: 201/redirect; el capital del socio en ese periodo aumenta en
   S/ 100.00.
3. Repetir con `monto=0`.
4. **Esperado**: rechazo de validación (FR-001).

## Escenario 2 — Aporte mensual al día (US2)

1. `POST /socios/{socioId}/periodos/{periodoId}/aporte-mensual` con
   `mes=2026-01`, `montoPagado=50.00`, `fecha=2026-01-31`.
2. **Esperado**: el mes queda `PAGADO`; capital acumulado del socio +S/ 50.00.

## Escenario 3 — Morosidad: caso S/105 (US3, SC-001)

1. No registrar el aporte de enero (`2026-01` queda `PENDIENTE`).
2. `GET /socios/{socioId}/periodos/{periodoId}/deuda` en febrero.
3. **Esperado**: total = S/ 105.00 exactos (Ene S/ 50 + Feb S/ 50 + 1 multa
   S/ 5), igual que SC-001.
4. `POST /socios/{socioId}/periodos/{periodoId}/aporte-mensual` con
   `montoPagado=105.00`.
5. **Esperado**: ambos meses quedan `PAGADO`, la multa queda saldada.

## Escenario 4 — Morosidad acumulada: caso S/160 (US3, SC-002)

1. Repetir el Escenario 3 pero sin pagar tampoco febrero; consultar la deuda en
   marzo.
2. **Esperado**: total = S/ 160.00 exactos (Ene+Feb+Mar S/ 50 c/u + 2 multas
   S/ 5 c/u), igual que SC-002.

## Escenario 5 — Rechazos (FR-009, FR-010, FR-011)

1. Repetir el Escenario 3 pagando `montoPagado=100.00` (menor) → **rechazo**
   ("el monto debe coincidir exactamente con...").
2. Repetir pagando `montoPagado=110.00` (mayor) → **rechazo** (mismo mensaje).
3. Repetir el pago de un mes ya `PAGADO` → **rechazo** ("este mes ya fue
   registrado como pagado").
4. Cerrar el periodo (`estado = CERRADO`, vía módulo Apertura de periodo) e
   intentar cualquiera de los POST anteriores → **rechazo** ("periodo cerrado,
   no se admiten aportes").

## Trazabilidad

Cada escenario anterior corresponde 1:1 a un Acceptance Scenario de `spec.md`
(US1–US3) y a los criterios SC-001–SC-004. Las pruebas automatizadas
equivalentes viven en `src/test/java/com/mibanquito/unit/MorosidadServiceTest.java`
y `AporteControllerIT.java` (ver `plan.md` → Project Structure).
