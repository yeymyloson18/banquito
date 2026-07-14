<!--
Sync Impact Report
==================
Version change: [TEMPLATE UNFILLED] → 1.2.0
Type: Initial ratification (drafted through internal revisions 1.0.0 → 1.1.0 → 1.2.0 before first commit to this file)

Modified principles: N/A (first concrete fill of the template; no prior ratified text existed)

Added sections:
- 1. Propósito del proyecto
- 2. Principios rectores (no negociables) — I a XVI:
  I. La especificación es la fuente de verdad
  II. Reglas de negocio explícitas y verificables
  III. Test-first para lógica de negocio
  IV. Simplicidad sobre sofisticación
  V. Trazabilidad completa
  VI. Protección de datos personales
  VII. La constitución no contiene reglas volátiles
  VIII. Dinero siempre como BigDecimal
  IX. Seguridad no es opcional
  X. Todo movimiento pertenece a un periodo anual
  XI. Nunca se presta más dinero del que existe
  XII. El interés se calcula sobre saldo pendiente, no sobre el monto original
  XIII. La distribución de utilidades es proporcional al capital, nunca igualitaria
  XIV. Un periodo cerrado es inmutable
  XV. Un periodo no se cierra con préstamos pendientes
  XVI. La morosidad es acumulativa mes a mes
- 3. Restricciones técnicas obligatorias
- 4. Roles del sistema
- 5. Módulos del sistema (ciclo anual)
- 6. Gobernanza
- 7. Historial de versiones

Removed sections: none (generic template placeholders replaced with concrete project content)

Templates requiring updates:
- ✅ .specify/templates/plan-template.md — "Constitution Check" gate is generic/data-driven ([Gates determined based on constitution file]); no edit needed, gates will be derived from this file's principles at plan time.
- ✅ .specify/templates/spec-template.md — generic, technology-agnostic; no constitution-specific references to update.
- ✅ .specify/templates/tasks-template.md — generic, technology-agnostic; no constitution-specific references to update.
- ✅ .specify/templates/checklist-template.md — not reviewed for constitution-specific content (generic checklist scaffold); no changes required.
- ⚠ README.md / docs/quickstart.md — do not exist yet in this repo; no runtime guidance doc to sync. Create when onboarding docs are needed, referencing this constitution.

Follow-up TODOs: none — all placeholders resolved with concrete values supplied by the user.
-->

# Mi Banquito Constitution

## 1. Propósito del proyecto

Mi Banquito es un sistema web que digitaliza la gestión de un banco comunal
que opera en periodos anuales: los socios aportan capital, solicitan
préstamos con interés, y al cierre de cada periodo se distribuyen las
ganancias proporcionalmente al capital aportado.

## 2. Principios rectores (no negociables)

### I. La especificación es la fuente de verdad
Ningún código se escribe sin que exista antes un `spec.md` aprobado.

### II. Reglas de negocio explícitas y verificables
Toda regla financiera vive en `spec.md` con al menos una prueba unitaria que
la verifique.

### III. Test-first para lógica de negocio
Reglas críticas (interés, morosidad, distribución de utilidades, validación
de caja) siguen TDD: prueba falla → código mínimo → prueba pasa.

### IV. Simplicidad sobre sofisticación
Monolito Spring Boot MVC por capas. Complejidad adicional se justifica en
`plan.md`.

### V. Trazabilidad completa
Cada tarea en `tasks.md` se rastrea a un requisito de `spec.md`. Cada commit
referencia su tarea.

### VI. Protección de datos personales
Minimización de datos y control de acceso por rol (Ley N.º 29733, Perú).

### VII. La constitución no contiene reglas volátiles
Montos, tasas, multas y fechas específicas de un periodo NUNCA se escriben
aquí — van en `spec.md`. Aquí solo van los invariantes que definen *cómo
funciona el sistema*, sin importar qué cifra esté vigente.

### VIII. Dinero siempre como BigDecimal
Ningún monto, tasa o saldo usa `float`/`double`.

### IX. Seguridad no es opcional
Spring Security, BCrypt, control de acceso por rol, auditoría de operaciones
financieras críticas.

### X. Todo movimiento pertenece a un periodo anual
Aportes, préstamos, intereses y multas siempre están asociados a un periodo
específico y son trazables a él. Nunca hay movimientos "sueltos" sin
periodo.

### XI. Nunca se presta más dinero del que existe
`Capital disponible = Caja − préstamos entregados`. Esta validación es una
barrera dura en el código (excepción/rechazo), nunca solo una advertencia en
la interfaz.

### XII. El interés se calcula sobre saldo pendiente, no sobre el monto original
Todo cálculo de interés mensual usa el saldo pendiente actual del préstamo
en ese momento, nunca el monto original desembolsado.

### XIII. La distribución de utilidades es proporcional al capital, nunca igualitaria
`Utilidad del socio = (capital aportado del socio / capital total) × ganancia total`.
Repartir en partes iguales viola este invariante y no debe ser posible en
el sistema.

### XIV. Un periodo cerrado es inmutable
Una vez cerrado un periodo (Regla 15), ningún dato de ese periodo puede
modificarse: ni aportes, ni préstamos, ni distribución. Solo lectura.

### XV. Un periodo no se cierra con préstamos pendientes
El cierre anual es una operación bloqueada por precondición: si existe al
menos un préstamo con saldo pendiente, el cierre debe rechazarse.

### XVI. La morosidad es acumulativa mes a mes
Un aporte impago se arrastra al mes siguiente junto con el aporte vigente y
la multa correspondiente, y sigue arrastrándose mientras no se cancele por
completo. El *monto* de la multa es parámetro de `spec.md`; el *mecanismo*
de arrastre es invariante.

## 3. Restricciones técnicas obligatorias

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.x (Web, Data JPA, Security, Validation) |
| Base de datos | MySQL 8, con Flyway como autoridad única del esquema |
| Frontend | Thymeleaf + Bootstrap 5 (responsive, mobile-first) |
| Pruebas unitarias | JUnit 5 + Mockito (obligatorias para lógica financiera) |
| Pruebas de integración | Testcontainers (opcional, requiere Docker Desktop) |
| Control de versiones | Git + GitHub; commit al cierre de cada fase SDD |
| Build | Maven / Maven Wrapper |

## 4. Roles del sistema

- **ADMINISTRADOR**: registra socios, registra aportes, aprueba/rechaza préstamos, registra pagos, cierra el periodo, ve todos los reportes.
- **SOCIO**: consulta su estado de cuenta, préstamos, aportes y ganancias. No modifica nada.

## 5. Módulos del sistema (ciclo anual)

1. Autenticación y seguridad
2. Apertura y gestión de periodos anuales
3. Registro y gestión de socios
4. Aportes (inicial, mensual, morosidad)
5. Préstamos (solicitud, aprobación, interés, pagos, pago anticipado)
6. Caja y dinero disponible (invariante transversal, Regla 12)
7. Estado de cuenta del socio
8. Reportes
9. Cierre anual y distribución de utilidades
10. Renovación de socios para el siguiente periodo

## 6. Gobernanza

Prevalece sobre `plan.md`/`tasks.md`. Cambios se versionan en semver. No se
recrea salvo que cambie un invariante o tecnología oficial.

## 7. Historial de versiones

| Versión | Fecha | Cambio |
|---|---|---|
| 1.0.0 | 2026-07-14 | Ratificación inicial |
| 1.1.0 | 2026-07-14 | + Java 21, BigDecimal, BCrypt, Flyway, módulos ciclo anual |
| 1.2.0 | 2026-07-14 | + Invariantes X-XVI extraídos de reglas de negocio reales; roles ajustados a Administrador/Socio |

**Version**: 1.2.0 | **Ratified**: 2026-07-14 | **Last Amended**: 2026-07-14
