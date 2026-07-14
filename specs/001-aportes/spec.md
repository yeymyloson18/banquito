# Feature Specification: Aportes

**Feature Branch**: `001-aportes`

**Created**: 2026-07-14

**Status**: Draft

**Input**: User description: "Especificación: Aportes — gestión del aporte inicial libre de cada socio al ingresar, el aporte mensual obligatorio de monto fijo mientras dure el periodo, y el cálculo automático de morosidad (aportes vencidos + multas) cuando un socio deja de pagar uno o más meses."

**Depends on**: Registro de socios, Apertura de periodo anual

## Clarifications

### Session 2026-07-14

- Q: Si el Administrador intenta registrar un aporte mensual para un socio/mes/periodo que ya figura como "pagado", ¿qué debe hacer el sistema? → A: Rechazar el registro duplicado — el sistema no permite más de un AporteMensual por socio+periodo+mes.
- Q: ¿Debe el sistema rechazar explícitamente el registro de cualquier aporte (inicial o mensual) contra un periodo que ya está cerrado? → A: Sí, rechazar siempre — ningún aporte puede registrarse, editarse ni eliminarse en un periodo con estado "cerrado" (Regla XIV).
- Q: Si un socio en mora paga un monto mayor al total de su deuda acumulada exacta, ¿qué debe hacer el sistema con el excedente? → A: Rechazar el monto exacto distinto — el pago debe coincidir exactamente con la deuda calculada; no se generan saldos a favor.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar aporte inicial (Priority: P1)

Como Administrador, quiero registrar el aporte inicial de un socio nuevo, para
que ese monto forme parte de su capital desde el inicio del periodo.

**Why this priority**: Es el punto de entrada de todo socio al sistema de
capital. Sin este registro no existe una base de capital sobre la cual
calcular aportes mensuales, morosidad, ni distribución de utilidades a fin
de periodo. Es la funcionalidad más simple y con menos dependencias, por lo
que puede entregarse primero como base del módulo.

**Independent Test**: Puede probarse por completo registrando un socio en un
periodo abierto para nuevos ingresos y verificando que, tras registrar un
aporte inicial, el capital del socio en ese periodo refleja el monto
ingresado.

**Acceptance Scenarios**:

1. **Given** un socio recién registrado en un periodo abierto para nuevos
   ingresos, **When** el Administrador registra un aporte inicial con un
   monto mayor a S/ 0, **Then** el sistema crea el registro asociado al
   socio y al periodo, y lo suma al capital del socio en ese periodo.
2. **Given** un socio recién registrado, **When** el Administrador intenta
   registrar un aporte inicial con monto igual o menor a S/ 0, **Then** el
   sistema rechaza el registro.

---

### User Story 2 - Registrar aporte mensual al día (Priority: P2)

Como Administrador, quiero registrar el aporte mensual de un socio que está
al día, para mantener su historial de aportes correcto mes a mes.

**Why this priority**: Es la operación recurrente más frecuente del módulo
(se repite cada mes por cada socio activo) y reemplaza directamente el
registro manual en cuaderno/Excel que hoy genera pérdida de trazabilidad.
Depende de que exista un socio con capital inicial (US1).

**Independent Test**: Puede probarse por completo con un socio sin meses
anteriores impagos: se registra el pago del aporte del mes vigente por el
monto vigente del periodo y se verifica que el mes queda marcado como
pagado y que el capital acumulado del socio aumenta en ese monto.

**Acceptance Scenarios**:

1. **Given** un socio activo sin meses anteriores impagos en el periodo,
   **When** se registra el pago del aporte del mes vigente por el monto
   vigente del periodo, **Then** el sistema marca ese mes como pagado y lo
   suma al capital acumulado del socio.
2. **Given** un socio que ingresó a mitad de año, **When** se calculan sus
   meses obligatorios, **Then** el primer mes obligatorio es el mes de
   ingreso, no enero.

---

### User Story 3 - Calcular y cobrar morosidad (Priority: P3)

Como Administrador, quiero que el sistema calcule automáticamente cuánto
debe un socio que dejó de pagar uno o más meses, para cobrarle el monto
correcto incluyendo multas.

**Why this priority**: Es la funcionalidad de mayor valor diferencial del
módulo (resuelve directamente el problema de "perder el rastro de quién
debe qué"), pero es también la más compleja y depende de que ya existan
aportes mensuales registrados (US2) para tener meses impagos que arrastrar.

**Independent Test**: Puede probarse por completo con un socio que dejó de
pagar uno o más meses: se solicita el cálculo de deuda acumulada y se
verifica que el total coincide exactamente con la suma de aportes vencidos
(al monto vigente de cada mes) más el aporte del mes actual más una multa
por cada mes vencido.

**Acceptance Scenarios**:

1. **Given** un socio con uno o más aportes mensuales impagos, **When** el
   sistema calcula lo que debe pagar en el mes actual, **Then** el total =
   suma de todos los aportes mensuales impagos (al monto vigente de cada
   mes) + el aporte del mes actual + una multa por cada mes vencido.
2. **Given** un socio no pagó enero y se pone al día en febrero (aporte
   mensual vigente S/ 50, multa vigente S/ 5), **When** el sistema calcula
   su deuda, **Then** el total es exactamente S/ 105 (Ene S/ 50 + Feb S/ 50
   + 1 multa de S/ 5).
3. **Given** el mismo socio tampoco pagó febrero y se pone al día en marzo,
   **When** el sistema calcula su deuda, **Then** el total es exactamente
   S/ 160 (Ene S/ 50 + Feb S/ 50 + Mar S/ 50 + 2 multas de S/ 5 = S/ 10).
4. **Given** un socio en mora, **When** intenta pagar solo el aporte del mes
   actual sin cubrir lo vencido, **Then** el sistema rechaza el pago parcial
   y exige el total acumulado (meses vencidos + multas + mes actual).

---

### Edge Cases

- Un socio se pone al día pagando varios meses vencidos + multas en una sola
  transacción: el sistema debe registrar cada aporte mensual y su multa
  correspondiente de forma individual y trazable, aunque el pago se reciba
  como un solo monto.
- Un socio ingresa a mitad de año: su primer mes obligatorio es el mes de
  ingreso, no enero; no se generan aportes ni morosidad para meses previos a
  su ingreso.
- El monto del aporte mensual o de la multa cambia a mitad de periodo: el
  cambio solo aplica hacia adelante; los meses ya vencidos mantienen el
  monto vigente en el momento en que vencieron.
- Un socio en mora paga un monto distinto (mayor o menor) al total exacto de
  su deuda acumulada: el sistema rechaza el pago en ambos casos y no genera
  saldos a favor ni deja deuda parcialmente cubierta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir registrar un aporte inicial por
  socio, de monto libre pero mayor a S/ 0, asociado a un periodo.
- **FR-002**: El sistema MUST permitir registrar aportes mensuales por
  socio, mes y periodo.
- **FR-003**: El monto del aporte mensual y el de la multa por mora MUST ser
  parámetros configurables del periodo, nunca valores fijos en el código.
- **FR-004**: El sistema MUST calcular automáticamente, en cualquier
  momento, la deuda acumulada de aportes de cualquier socio (meses impagos
  al monto vigente de cada mes + multas correspondientes).
- **FR-005**: El sistema MUST registrar cada pago con fecha, monto y detalle
  del concepto (aporte de qué mes, o multa de qué mes).
- **FR-006**: Las multas de mora MUST sumarse a la caja del banco y MUST
  contabilizarse como parte de la ganancia distribuible al cierre del
  periodo, sujeta a la distribución proporcional al capital (Regla XIII de
  la constitución).
- **FR-007**: El sistema MUST determinar el primer mes obligatorio de aporte
  de un socio a partir de su mes de ingreso al periodo, no desde enero.
- **FR-008**: El sistema MUST preservar, para cada mes ya vencido, el monto
  de aporte y de multa vigentes en el momento en que ese mes venció, aun si
  los parámetros del periodo cambian posteriormente.
- **FR-009**: El sistema MUST exigir que el pago de un socio en mora
  coincida exactamente con el 100% de su deuda acumulada (meses vencidos +
  multas + aporte del mes actual); MUST rechazar tanto pagos parciales
  (monto menor) como sobrepagos (monto mayor) sin generar saldos a favor.
- **FR-010**: El sistema MUST rechazar el registro de un aporte mensual para
  una combinación socio+periodo+mes que ya tiene estado "pagado"; no puede
  existir más de un `AporteMensual` para la misma combinación.
- **FR-011**: El sistema MUST rechazar el registro, edición o eliminación de
  cualquier aporte (inicial o mensual) asociado a un periodo con estado
  "cerrado" (Regla XIV de la constitución: un periodo cerrado es
  inmutable).

### Key Entities

- **AporteInicial**: aporte de capital único registrado al ingreso de un
  socio a un periodo. Atributos: socio, periodo, monto, fecha.
- **AporteMensual**: aporte obligatorio de un socio correspondiente a un mes
  específico de un periodo. Atributos: socio, periodo, mes, monto esperado
  (histórico al momento del vencimiento), fecha de pago, estado
  (pendiente/pagado). La combinación socio+periodo+mes es única: no puede
  existir más de un registro para la misma combinación.
- **MultaMora**: penalidad generada por un aporte mensual pagado fuera de
  plazo. Atributos: aporte mensual asociado, monto, mes en que se generó.
- **ParametroPeriodo**: configuración vigente de un periodo. Atributos:
  periodo, monto de aporte mensual vigente, monto de multa vigente, fecha
  desde la cual rige.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El caso de un socio que no pagó enero y se pone al día en
  febrero calcula una deuda total exacta de S/ 105.
- **SC-002**: El caso de un socio que tampoco pagó febrero y se pone al día
  en marzo calcula una deuda total exacta de S/ 160.
- **SC-003**: El 100% de los aportes (iniciales y mensuales) y multas
  quedan trazables a un socio, un periodo y una fecha específicos.
- **SC-004**: El Administrador puede consultar la deuda acumulada de
  cualquier socio en cualquier momento sin necesidad de cálculos manuales
  externos al sistema.

## Assumptions

- No existe un monto mínimo obligatorio para el aporte inicial, pero debe
  ser mayor a S/ 0.
- El medio de pago (efectivo, transferencia, etc.) no forma parte de este
  módulo; el sistema registra el monto y la fecha, no el canal de pago.
- Este módulo depende de que ya existan los módulos de Registro de socios y
  Apertura de periodo anual (un socio y un periodo abierto son
  prerrequisitos de cualquier aporte).

## Out of Scope

- Solicitud y aprobación de préstamos.
- Cierre anual y distribución de utilidades.
- Selección o registro del medio de pago (efectivo, transferencia, etc.).
