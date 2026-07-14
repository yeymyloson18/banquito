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

## Cálculo derivado: `AporteService.calcularDeudaAcumulada` (FR-004)

No es una entidad persistida, sino un valor calculado por el Service:

```
calcularDeudaAcumulada(socio, periodo, mesActual) =
    Σ montoEsperado de cada AporteMensual PENDIENTE con mes <= mesActual
  + Σ monto de cada MultaMora asociada a esos AporteMensual PENDIENTE
```

Verificado exactamente por dos casos de `AporteServiceTest`:
- 1 mes vencido (enero, pagado en febrero) → **S/105** = S/50 (ene) + S/50 (feb)
  + 1 multa S/5.
- 2 meses vencidos (enero y febrero, pagados en marzo) → **S/160** = S/50 × 3
  (ene+feb+mar) + 2 multas S/5.

## Excepciones de dominio (`com.mibanquito.aportes.service`)

- **`PagoParcialNoPermitidoException`** (`RuntimeException`): lanzada por
  `registrarAporteMensual` cuando el socio está en mora y el monto pagado no
  coincide exactamente con `calcularDeudaAcumulada` (FR-009).
- **`PeriodoCerradoException`** (`RuntimeException`): lanzada por
  `registrarAporteInicial` y `registrarAporteMensual` cuando
  `periodo.estado == CERRADO` (FR-011, Principio XIV).

## Relaciones

```
periodo_anual (1) ── (N) ParametroPeriodo
periodo_anual (1) ── (N) AporteInicial
periodo_anual (1) ── (N) AporteMensual
socio         (1) ── (N) AporteInicial
socio         (1) ── (N) AporteMensual
AporteMensual (1) ── (0..1) MultaMora
```
