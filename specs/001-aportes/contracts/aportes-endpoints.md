# Contracts: Aportes (endpoints Thymeleaf/MVC)

La aplicación es un monolito server-rendered (Thymeleaf), no una API JSON pública.
El "contrato" aquí documentado son las rutas HTTP que expone `AporteController`,
sus parámetros de entrada y el comportamiento observable (vista devuelta o error),
todos restringidos al rol `ADMINISTRADOR` (Principio IX).

## GET /socios/{socioId}/periodos/{periodoId}/aporte-inicial

Muestra el formulario de registro de aporte inicial.

- **Rol requerido**: ADMINISTRADOR
- **Respuesta**: vista `aportes/aporte-inicial-form.html`

## POST /socios/{socioId}/periodos/{periodoId}/aporte-inicial

Registra el aporte inicial de un socio (US1, FR-001).

- **Rol requerido**: ADMINISTRADOR
- **Body (form)**: `monto: BigDecimal`, `fecha: LocalDate`
- **200 / redirect**: aporte creado, capital del socio actualizado
- **Rechazo (FR-001)**: `monto <= 0` → error de validación en el mismo formulario
- **Rechazo (FR-011)**: `periodo.estado == CERRADO` → error "periodo cerrado, no se admiten aportes"

## GET /socios/{socioId}/periodos/{periodoId}/aporte-mensual

Muestra el formulario de registro de aporte mensual, incluyendo la deuda
acumulada actual del socio si aplica (FR-004).

- **Rol requerido**: ADMINISTRADOR
- **Respuesta**: vista `aportes/aporte-mensual-form.html` con el total a pagar
  precalculado (deuda acumulada + aporte del mes actual, si hay meses vencidos)

## POST /socios/{socioId}/periodos/{periodoId}/aporte-mensual

Registra el pago del aporte mensual (US2, US3, FR-002, FR-004, FR-009, FR-010).

- **Rol requerido**: ADMINISTRADOR
- **Body (form)**: `mes: YearMonth`, `montoPagado: BigDecimal`, `fecha: LocalDate`
- **200 / redirect**: mes(es) marcados como `PAGADO`, multas asociadas saldadas
- **Rechazo (FR-009)**: `montoPagado != deudaAcumulada` (ni más ni menos) → error
  "el monto debe coincidir exactamente con S/ {deudaAcumulada}"
- **Rechazo (FR-010)**: ya existe `AporteMensual` en estado `PAGADO` para ese
  socio+periodo+mes → error "este mes ya fue registrado como pagado"
- **Rechazo (FR-011)**: `periodo.estado == CERRADO` → error "periodo cerrado, no
  se admiten aportes"

## GET /socios/{socioId}/periodos/{periodoId}/deuda

Consulta la deuda acumulada actual del socio (US3, FR-004, SC-004).

- **Rol requerido**: ADMINISTRADOR
- **Respuesta**: vista `aportes/deuda-socio.html` con el desglose: meses
  vencidos (con su `montoEsperado` histórico), multas por mes, y total exacto.
