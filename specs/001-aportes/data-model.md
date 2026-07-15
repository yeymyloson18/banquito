# Data Model: Aportes

Paquete: `com.mibanquito.aportes.entity` (convención de `docs/arquitectura-general.md`).
Todo campo monetario usa `BigDecimal` (Principio VIII). Toda entidad transaccional
lleva FK a `periodo_anual` (Principio X). `Socio` y `PeriodoAnual` son entidades
externas (propiedad de otros módulos); aquí solo se documenta lo que este feature
consume por referencia.

## Entidades externas referenciadas (no creadas por este feature)

### Socio *(módulo Registro de socios)*
- `id` (PK)

### PeriodoAnual *(módulo Apertura de periodo anual, tabla `periodo_anual`)*
- `id` (PK)
- `estado`: enum `ABIERTO` | `CERRADO` — consumido por FR-011

## Entidades nuevas de este feature (`com.mibanquito.aportes.entity`)

### ParametroPeriodo

Historial de configuración vigente de aporte mensual y multa por periodo (ver
`research.md` §2).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `periodoId` | Long (FK → periodo_anual) | obligatorio |
| `montoAporteMensual` | `BigDecimal(10,2)` | > 0 (FR-003) |
| `montoMulta` | `BigDecimal(10,2)` | > 0 (FR-003) |
| `vigenteDesde` | `LocalDate` | obligatorio; determina qué fila aplica a un mes dado |

**Regla de resolución**: para un mes M, el parámetro vigente es la fila con
`vigenteDesde` más reciente `<=` fecha de vencimiento de M.

### AporteInicial

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `socioId` | Long (FK → socio) | obligatorio |
| `periodoId` | Long (FK → periodo_anual) | obligatorio; periodo MUST estar `ABIERTO` (FR-011) |
| `monto` | `BigDecimal(10,2)` | MUST ser > 0 (FR-001) |
| `fecha` | `LocalDate` | obligatorio |

**Validación de escritura** (`AporteService.registrarAporteInicial`): rechaza si
`periodo.estado == CERRADO` (`PeriodoCerradoException`, FR-011) o si `monto <= 0`.

### AporteMensual

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `socioId` | Long (FK → socio) | obligatorio |
| `periodoId` | Long (FK → periodo_anual) | obligatorio |
| `mes` | `YearMonth` | obligatorio; MUST ser >= mes de ingreso del socio (FR-007) |
| `montoEsperado` | `BigDecimal(10,2)` | **histórico e inmutable**: snapshot del `ParametroPeriodo.montoAporteMensual` vigente al vencer este mes (FR-008); NUNCA se recalcula si `ParametroPeriodo` cambia después |
| `fechaPago` | `LocalDate` (nullable) | null mientras `estado == PENDIENTE` |
| `estado` | enum `EstadoAporteMensual` (`PENDIENTE` \| `PAGADO`) | transición única PENDIENTE → PAGADO |

**Restricción de unicidad**: `UNIQUE (socio_id, periodo_id, mes)` en la migración
Flyway — no puede existir más de un `AporteMensual` para la misma combinación
(FR-010).

**Piso de generación (FR-007, FR-013)**: el mes de ingreso del socio a un
periodo se deriva de `AporteInicial.fecha` (no es un campo propio ni de
`Socio` ni de `AporteMensual`). Ningún `AporteMensual` se genera para un mes
anterior a `YearMonth.from(AporteInicial.fecha)` de ese socio+periodo.

**Transición de estado**:
```
PENDIENTE --(AporteService.registrarAporteMensual, pago exacto)--> PAGADO
PAGADO    --(nuevo intento de registro)--> rechazado (FR-010, no es una transición)
```

**Validación de escritura** (`AporteService.registrarAporteMensual`):
1. Rechaza si `periodo.estado == CERRADO` (`PeriodoCerradoException`, FR-011).
2. Rechaza si ya existe un `AporteMensual` `PAGADO` para socio+periodo+mes
   (FR-010).
3. Si el socio está en mora (existe algún mes `PENDIENTE` anterior al mes
   actual) y el monto recibido no coincide **exactamente** con
   `calcularDeudaAcumulada(...)`, lanza `PagoParcialNoPermitidoException`
   (FR-009) — cubre tanto pago parcial (monto menor) como sobrepago (monto
   mayor).

### MultaMora

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `aporteMensualId` | Long (FK → AporteMensual, único) | un `AporteMensual` vencido genera como máximo una `MultaMora` |
| `monto` | `BigDecimal(10,2)` | **histórico e inmutable**: snapshot del `ParametroPeriodo.montoMulta` vigente al momento en que el mes venció (FR-008); NUNCA se recalcula |
| `mesGenerada` | `YearMonth` | igual al mes del `AporteMensual` asociado |

**Regla de negocio**: el total de multas de un socio en un momento dado = 1
multa por cada `AporteMensual` en estado `PENDIENTE` cuyo mes ya venció (FR-004,
FR-006, Principio XVI).

## Generación perezosa de `AporteMensual` (FR-008, FR-012, FR-013)

No existe un job/batch separado que cree los `AporteMensual` de cada mes. En
su lugar, tanto `calcularDeudaAcumulada` como `registrarAporteMensual`
comparten una operación interna `resolverMesesHasta(socio, periodo, mesHasta)`
que, para cada mes entre `max(mesIngreso, último mes ya generado)` y
`mesHasta` (inclusive):

1. Si ya existe un `AporteMensual` para ese mes, lo reutiliza.
2. Si no existe, lo crea en estado `PENDIENTE`, resolviendo el
   `ParametroPeriodo` vigente a la fecha de vencimiento de ese mes y
   congelando el resultado en `montoEsperado` (histórico e inmutable,
   FR-008). Nunca se genera un mes anterior a `mesIngreso` (FR-007, FR-013).
3. Si el mes recién generado ya está vencido respecto al mes actual del
   sistema, genera también su `MultaMora` asociada, con el `montoMulta`
   vigente en ese mismo momento.

## Cálculo derivado: `AporteService.calcularDeudaAcumulada` (FR-004)

No es una entidad persistida, sino un valor calculado por el Service:

```
calcularDeudaAcumulada(socio, periodo, mesHasta) =
    resolverMesesHasta(socio, periodo, mesHasta)
  + Σ montoEsperado de cada AporteMensual PENDIENTE con mes <= mesHasta
  + Σ monto de cada MultaMora asociada a esos AporteMensual PENDIENTE
```

Verificado exactamente por dos casos de `AporteServiceTest`:
- 1 mes vencido (enero, pagado en febrero) → **S/105** = S/50 (ene) + S/50 (feb)
  + 1 multa S/5.
- 2 meses vencidos (enero y febrero, pagados en marzo) → **S/160** = S/50 × 3
  (ene+feb+mar) + 2 multas S/5.

## Pago consolidado multi-mes: `AporteService.registrarAporteMensual` (FR-012)

Firma: `registrarAporteMensual(socioId, periodoId, mesHasta, montoPagado, fecha)`.

`mesHasta` significa "el socio está pagando hasta este mes inclusive", no
"solo este mes puntual". El Service:

1. Ejecuta `resolverMesesHasta(socio, periodo, mesHasta)` (genera lo que
   falte).
2. Rechaza si `periodo.estado == CERRADO` (`PeriodoCerradoException`, FR-011).
3. Rechaza si el `AporteMensual` de `mesHasta` ya está `PAGADO`
   (`AporteMensualYaPagadoException` o excepción equivalente, FR-010).
4. Calcula `calcularDeudaAcumulada(socio, periodo, mesHasta)` y exige que
   `montoPagado` coincida exactamente (ni más ni menos, FR-009); si no,
   lanza `PagoParcialNoPermitidoException`.
5. Si coincide, marca **todos** los `AporteMensual` con mes `<= mesHasta` en
   estado `PENDIENTE` como `PAGADO` (con `fechaPago = fecha`) en una única
   transacción `@Transactional`. Sus `MultaMora` asociadas quedan saldadas
   implícitamente: `calcularDeudaAcumulada` solo suma multas de
   `AporteMensual` en estado `PENDIENTE`, así que una vez `PAGADO` dejan de
   contar sin necesidad de un campo `saldada` adicional.

Este mecanismo es el que hace verificables exactamente los casos S/105 y
S/160 también a través de `registrarAporteMensual` (no solo a través de
`calcularDeudaAcumulada` de forma aislada).

## Integración con el módulo Caja (FR-006)

Este feature **no** implementa el módulo Caja (constitución §5, módulo 6).
Expone:

- `MultaMoraRepository` con un método de agregación por periodo (p. ej.
  `sumMontoByPeriodoId(periodoId): BigDecimal`).
- `AporteService.obtenerTotalMultasPeriodo(periodoId): BigDecimal`, que
  envuelve esa consulta para que otros módulos la consuman a través de la
  capa Service (no directamente del Repository), conforme a
  `docs/arquitectura-general.md`.

La actualización real del saldo de caja y su inclusión en la ganancia
distribuible al cierre (Principio XIII) son responsabilidad de los módulos
Caja y Cierre anual, que consumirán este dato — Aportes solo garantiza que
cada multa queda persistida y sumable.

## Seguridad (FR-014)

`AporteController` restringe sus 3 rutas (aporte inicial, aporte mensual,
consulta de deuda) al rol `ADMINISTRADOR` mediante
`@PreAuthorize("hasRole('ADMINISTRADOR')")` a nivel de método, conforme a
`docs/arquitectura-general.md` §Seguridad. Se asume que la configuración base
de Spring Security (`@EnableMethodSecurity`, autenticación, roles) la provee
el módulo Autenticación (fuera de alcance de este feature).

## Excepciones de dominio (`com.mibanquito.aportes.service`)

- **`PagoParcialNoPermitidoException`** (`RuntimeException`): lanzada por
  `registrarAporteMensual` cuando el socio está en mora y el monto pagado no
  coincide exactamente con `calcularDeudaAcumulada` (FR-009).
- **`PeriodoCerradoException`** (`RuntimeException`): lanzada por
  `registrarAporteInicial` y `registrarAporteMensual` cuando
  `periodo.estado == CERRADO` (FR-011, Principio XIV).
- **`AporteMensualYaPagadoException`** (`RuntimeException`): lanzada por
  `registrarAporteMensual` cuando el `AporteMensual` de `mesHasta` (o
  cualquier mes dentro del rango a saldar) ya está en estado `PAGADO`
  (FR-010).

## Relaciones

```
periodo_anual (1) ── (N) ParametroPeriodo
periodo_anual (1) ── (N) AporteInicial
periodo_anual (1) ── (N) AporteMensual
socio         (1) ── (N) AporteInicial
socio         (1) ── (N) AporteMensual
AporteMensual (1) ── (0..1) MultaMora
```
