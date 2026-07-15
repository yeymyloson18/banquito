# Contracts: Aportes (endpoints Thymeleaf/MVC)

La aplicación es un monolito server-rendered (Thymeleaf), no una API JSON pública.
El "contrato" aquí documentado son las rutas HTTP que expone `AporteController`,
sus parámetros de entrada y el comportamiento observable (vista devuelta o error),
todos restringidos al rol `ADMINISTRADOR` (Principio IX, FR-014).

**Aplicación de la restricción de rol**: cada método del controlador se anota
con `@PreAuthorize("hasRole('ADMINISTRADOR')")`. Un usuario con rol `SOCIO`
recibe 403/redirect en las 5 rutas siguientes — verificado por
`AporteControllerIT` (ver `tasks.md` Fase 8).

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

Registra el pago del aporte mensual, liquidando en una sola operación todos
los meses vencidos hasta `mesHasta` inclusive (US2, US3, FR-002, FR-004,
FR-009, FR-010, FR-012).

- **Rol requerido**: ADMINISTRADOR
- **Body (form)**: `mesHasta: YearMonth` (mes hasta el cual se está pagando,
  no un mes puntual — ver FR-012), `montoPagado: BigDecimal`, `fecha: LocalDate`
- **200 / redirect**: todos los `AporteMensual` `PENDIENTE` con mes `<= mesHasta`
  quedan `PAGADO`, sus multas asociadas quedan saldadas implícitamente
- **Rechazo (FR-009)**: `montoPagado != deudaAcumulada` (ni más ni menos) →
  `AporteService` lanza `PagoParcialNoPermitidoException`, traducida por el
  controlador a "el monto debe coincidir exactamente con S/ {deudaAcumulada}"
- **Rechazo (FR-010)**: ya existe `AporteMensual` en estado `PAGADO` para
  `mesHasta` (o algún mes dentro del rango) → `AporteService` lanza
  `AporteMensualYaPagadoException`, traducida a "este mes ya fue registrado
  como pagado"
- **Rechazo (FR-011)**: `periodo.estado == CERRADO` → error "periodo cerrado, no
  se admiten aportes"
- **Rechazo (FR-007/FR-013)**: `mesHasta` anterior al mes de ingreso del socio
  (`AporteInicial.fecha`) → no hay deuda que generar; el sistema no crea
  `AporteMensual` para meses previos al ingreso

## GET /socios/{socioId}/periodos/{periodoId}/deuda

Consulta la deuda acumulada actual del socio (US3, FR-004, SC-004).

- **Rol requerido**: ADMINISTRADOR
- **Respuesta**: vista `aportes/deuda-socio.html` con el desglose: meses
  vencidos (con su `montoEsperado` histórico), multas por mes, y total exacto.
- **Efecto lateral conocido** (decisión de diseño, no un bug): esta consulta
  puede generar y persistir `AporteMensual`/`MultaMora` nuevos si aún no
  existían para algún mes entre el ingreso del socio y el mes actual
  (generación perezosa, ver `data-model.md` §Generación perezosa). Es
  idempotente — llamadas repetidas no duplican filas — pero un `GET` que
  escribe se aparta de la convención HTTP habitual. Se acepta así por
  simplicidad (Principio IV): la alternativa (separar "resolver meses
  pendientes" de "calcular total" en dos pasos) añadiría una llamada extra
  sin beneficio funcional para este sistema de bajo volumen.
