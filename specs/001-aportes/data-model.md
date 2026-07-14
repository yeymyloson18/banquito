# Data Model: Aportes

Todas las entidades monetarias usan `BigDecimal` (Principio VIII). Todas las
entidades transaccionales llevan FK a `PeriodoAnual` (Principio X). `Socio` y
`PeriodoAnual` son entidades externas (propiedad de otros módulos); aquí solo se
documenta la porción de sus atributos que este feature consume por referencia.

## Entidades externas referenciadas (no creadas por este feature)

### Socio *(módulo Registro de socios)*
- `id` (PK)
- Atributos propios fuera de alcance de este feature.

### PeriodoAnual *(módulo Apertura de periodo anual)*
- `id` (PK)
- `estado`: enum `ABIERTO` | `CERRADO` — consumido por FR-011.

## Entidades nuevas de este feature

### ParametroPeriodo

Historial de configuración vigente de aporte mensual y multa para un periodo
(ver research.md §2).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `periodoId` | Long (FK → PeriodoAnual) | obligatorio |
| `montoAporteMensual` | `BigDecimal(10,2)` | > 0 (FR-003) |
| `montoMulta` | `BigDecimal(10,2)` | > 0 (FR-003) |
| `vigenteDesde` | `LocalDate` | obligatorio; determina qué fila aplica a un mes dado |

**Regla de resolución**: para un mes M, el parámetro vigente es la fila con
`periodoId` correspondiente y el `vigenteDesde` más reciente `<=` fecha de
vencimiento de M.

### AporteInicial

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `socioId` | Long (FK → Socio) | obligatorio |
| `periodoId` | Long (FK → PeriodoAnual) | obligatorio; periodo MUST estar `ABIERTO` (FR-011) |
| `monto` | `BigDecimal(10,2)` | MUST ser > 0 (FR-001) |
| `fecha` | `LocalDate` | obligatorio |

**Validación de escritura**: rechazar si `periodo.estado == CERRADO` (FR-011).

### AporteMensual

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `socioId` | Long (FK → Socio) | obligatorio |
| `periodoId` | Long (FK → PeriodoAnual) | obligatorio |
| `mes` | `YearMonth` (o `anio`+`mes` int) | obligatorio; MUST ser >= mes de ingreso del socio (FR-007) |
| `montoEsperado` | `BigDecimal(10,2)` | snapshot del `ParametroPeriodo.montoAporteMensual` vigente al vencer este mes (FR-008); inmutable una vez fijado |
| `fechaPago` | `LocalDate` (nullable) | null mientras `estado == PENDIENTE` |
| `estado` | enum `PENDIENTE` \| `PAGADO` | transición única PENDIENTE → PAGADO (FR-010) |

**Restricción de unicidad**: `UNIQUE (socioId, periodoId, mes)` — no puede existir
más de un `AporteMensual` para la misma combinación (FR-010).

**Transiciones de estado**:
```
PENDIENTE --(pago exacto registrado, FR-009)--> PAGADO
PAGADO    --(cualquier intento de registro nuevo)--> RECHAZADO (FR-010, no es una transición real, es un rechazo)
```

**Validación de escritura**: rechazar si `periodo.estado == CERRADO` (FR-011).

### MultaMora

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long (PK) | autogenerado |
| `aporteMensualId` | Long (FK → AporteMensual, único) | un `AporteMensual` vencido genera como máximo una `MultaMora` |
| `monto` | `BigDecimal(10,2)` | snapshot del `ParametroPeriodo.montoMulta` vigente al momento en que el mes venció (FR-008) |
| `mesGenerada` | `YearMonth` | mes en que se generó la multa (igual al mes del `AporteMensual` asociado) |

**Regla de negocio**: el total de multas de un socio en un momento dado = 1 multa
por cada `AporteMensual` en estado `PENDIENTE` cuyo mes ya venció (FR-004, FR-006,
Principio XVI).

## Cálculo derivado: Deuda acumulada (FR-004)

No es una entidad persistida, sino un valor calculado por `MorosidadService`:

```
deudaAcumulada(socio, periodo, mesActual) =
    Σ montoEsperado de cada AporteMensual PENDIENTE con mes <= mesActual
  + Σ monto de cada MultaMora asociada a esos AporteMensual PENDIENTE
```

Verificado exactamente por SC-001 (S/105 = S/50 + S/50 + S/5) y SC-002 (S/160 =
S/50×3 + S/5×2).

## Relaciones

```
PeriodoAnual (1) ── (N) ParametroPeriodo
PeriodoAnual (1) ── (N) AporteInicial
PeriodoAnual (1) ── (N) AporteMensual
Socio        (1) ── (N) AporteInicial
Socio        (1) ── (N) AporteMensual
AporteMensual (1) ── (0..1) MultaMora
```
