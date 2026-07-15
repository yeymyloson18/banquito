# Research: Aportes

**Phase 0 output** — el stack completo (Java 21, Spring Boot 3, MySQL 8 + Flyway,
Thymeleaf + Bootstrap 5, JUnit 5 + Mockito) y la convención de paquetes
(`com.mibanquito.aportes.{entity,repository,service,dto,controller}`) ya están
fijados por `.specify/memory/constitution.md` y `docs/arquitectura-general.md`. No
quedan marcadores `NEEDS CLARIFICATION` en el Technical Context. Este documento
resuelve las decisiones de diseño específicas del dominio Aportes.

## 1. Representación de dinero (Peruvian soles)

**Decision**: Todo campo monetario usa `BigDecimal` con `scale = 2` y
`RoundingMode.HALF_UP`, mapeado en JPA como `@Column(precision = 10, scale = 2)`.

**Rationale**: Principio VIII prohíbe `float`/`double`. `BigDecimal` con escala
fija permite comparación exacta (`compareTo`), imprescindible para que
`registrarAporteMensual` rechace cualquier monto que no coincida exactamente con
la deuda calculada.

**Alternatives considered**: enteros en centavos — descartado, sin ventaja para
el volumen de este sistema y peor legibilidad en las vistas Thymeleaf.

## 2. Historial inmutable de `montoEsperado` y `MultaMora.monto` (FR-008)

**Decision**: `ParametroPeriodo` es una tabla de historial (`periodo_id`,
`monto_aporte_mensual`, `monto_multa`, `vigente_desde`). Cuando `AporteService`
genera o resuelve un `AporteMensual` para un mes M, congela el
`ParametroPeriodo` vigente en ese momento en `AporteMensual.montoEsperado` y,
si corresponde, en `MultaMora.monto`. Estos valores **no se recalculan** aunque
`ParametroPeriodo` cambie después — instrucción explícita confirmada por el
usuario y ya requerida por FR-008.

**Rationale**: Es la única forma de que `calcularDeudaAcumulada` reproduzca
exactamente S/105 (Ene+Feb a S/50 + 1 multa S/5) y S/160 (Ene+Feb+Mar a S/50 +
2 multas S/5) sin importar si el parámetro del periodo cambió entre medio.

**Alternatives considered**: recalcular con el parámetro vigente al momento del
pago — rechazado explícitamente por el usuario y contradice FR-008 y el edge
case del spec ("los meses ya vencidos mantienen el monto vigente cuando
vencieron").

## 3. `registrarAporteMensual` y rechazo de pago parcial (`PagoParcialNoPermitidoException`)

**Decision**: `AporteService.registrarAporteMensual` calcula primero
`calcularDeudaAcumulada(socio, periodo, mesActual)`. Si el socio tiene algún mes
`PENDIENTE` anterior al mes actual (está en mora) y el monto recibido no
coincide **exactamente** con la deuda acumulada total, lanza
`PagoParcialNoPermitidoException` (unchecked, `RuntimeException`) antes de
persistir nada. Si el monto coincide exactamente, marca todos los
`AporteMensual` cubiertos como `PAGADO` y las `MultaMora` asociadas como
saldadas, en una única transacción (`@Transactional`).

**Rationale**: FR-009 exige rechazar tanto pagos parciales como sobrepagos; una
excepción de dominio explícita (en vez de un booleano o `Optional`) permite que
`AporteController` la traduzca a un mensaje de error claro sin que el Service
conozca detalles de HTTP/vistas (separación de capas de
`docs/arquitectura-general.md`).

**Alternatives considered**: devolver un resultado (`Either`/`ResultDTO`) en vez
de lanzar excepción — descartado por consistencia con el resto del monolito,
donde las reglas de negocio duras (Principio XI, "barrera dura... nunca solo una
advertencia") se expresan como excepciones que abortan la transacción.

## 4. Rechazo de operaciones sobre periodo cerrado (FR-011)

**Decision**: `AporteService` valida `periodo.getEstado() == CERRADO` al inicio
de `registrarAporteInicial` y `registrarAporteMensual`, lanzando una excepción
de dominio (`PeriodoCerradoException`) antes de cualquier escritura.

**Rationale**: Principio XIV es transversal a varios módulos futuros
(Préstamos, Cierre anual); centralizar la comprobación en el Service (no en el
Controller ni en la vista) asegura que se cumpla sin importar el punto de
entrada.

**Alternatives considered**: constraint a nivel de base de datos (trigger) —
descartado por complejidad adicional no justificada (Principio IV,
simplicidad); la validación en Service es suficiente dado que todas las
escrituras pasan por `AporteService`.

## 5. Unicidad socio+periodo+mes (FR-010)

**Decision**: índice único `UNIQUE (socio_id, periodo_id, mes)` en la migración
Flyway de `aporte_mensual`, además de la comprobación en `AporteService` antes
de insertar.

**Rationale**: doble barrera — el Service da el mensaje de error de negocio
claro, el índice de BD previene condiciones de carrera si dos administradores
registran el mismo pago casi simultáneamente.

**Alternatives considered**: solo validación en Service — descartado por riesgo
de condición de carrera sin barrera atómica en BD.

## 6. Testing test-first de `AporteService` (Principio III)

**Decision**: `AporteServiceTest` (JUnit 5 + Mockito, mocks de
`AporteMensualRepository` y `ParametroPeriodoRepository`) se escribe primero,
con al menos estos casos, y debe fallar contra un `AporteService` vacío antes
de implementarlo:
1. 1 mes vencido (enero) pagado en febrero → deuda total exacta S/105.
2. 2 meses vencidos (enero y febrero) pagados en marzo → deuda total exacta
   S/160.
3. Pago que no coincide exactamente con la deuda (mayor o menor) estando en
   mora → lanza `PagoParcialNoPermitidoException`.

Solo después de que estas pruebas pasen se escribe `AporteController`.

**Rationale**: exigencia directa del usuario y del Principio III; estas pruebas
son la verificación ejecutable de SC-001 y SC-002.

**Alternatives considered**: prueba de integración con Testcontainers/MySQL
real — fuera de este plan (no solicitada; constitución la marca opcional).

## 7. Generación perezosa de `AporteMensual` (resuelve U2 de `/speckit.analyze`)

**Decision**: No hay un job/batch mensual. `calcularDeudaAcumulada` y
`registrarAporteMensual` comparten una operación interna
`resolverMesesHasta(socio, periodo, mesHasta)` que genera bajo demanda los
`AporteMensual` `PENDIENTE` faltantes entre el mes de ingreso del socio y
`mesHasta`, congelando `montoEsperado` desde `ParametroPeriodo` vigente en
ese momento (ver `data-model.md`).

**Rationale**: Simplicidad (Principio IV) — evita introducir un scheduler o
job separado para un sistema de bajo volumen (decenas de socios); el mismo
resultado se logra resolviendo perezosamente en el único punto de entrada
donde se necesita (consulta de deuda o registro de pago).

**Alternatives considered**: job `@Scheduled` mensual que genera todos los
`AporteMensual` del periodo por adelantado — descartado por añadir
infraestructura (scheduler, posible tabla de control de ejecución) no
justificada para el volumen de este sistema.

## 8. Pago consolidado multi-mes: firma de `registrarAporteMensual` (resuelve U1 CRITICAL)

**Decision**: `registrarAporteMensual(socioId, periodoId, mesHasta, montoPagado, fecha)`.
`mesHasta` se interpreta como "pagar hasta este mes inclusive": el método
salda en una sola transacción todos los `AporteMensual` `PENDIENTE` con mes
`<= mesHasta`, no solo un mes puntual.

**Rationale**: El hallazgo U1 del `/speckit.analyze` detectó que un único
parámetro `mes` era ambiguo frente al edge case de "pago consolidado" y a
los propios casos SC-001/SC-002, que requieren saldar 2 y 3 meses en una
sola llamada. Nombrar el parámetro `mesHasta` (en vez de `mes`) hace
explícita la semántica de rango y elimina la ambigüedad tanto en el DTO
(`AporteMensualRequest`) como en el contrato HTTP.

**Alternatives considered**: mantener `mes` como mes puntual y exigir N
llamadas separadas (una por mes vencido) — descartado porque contradice
directamente el edge case de spec.md ("...aunque el pago se reciba como un
solo monto") y forzaría al Administrador a calcular manualmente cuántas
llamadas hacer, reintroduciendo el problema original que motivó este feature.

## 9. Alcance de FR-006 frente al módulo Caja (resuelve G1 CRITICAL)

**Decision**: Aportes no implementa el módulo Caja. Persiste cada
`MultaMora` de forma trazable y expone `AporteService.obtenerTotalMultasPeriodo(periodoId)`
(que envuelve una consulta agregada de `MultaMoraRepository`) para que el
futuro módulo Caja sume ese total a la caja del banco, y el módulo Cierre
anual lo incluya en la ganancia distribuible (Principio XIII).

**Rationale**: El hallazgo G1 detectó que FR-006 exigía una integración con
"la caja del banco" que no tiene dueño en este feature ni en el repo aún.
En vez de dejar el requisito sin cobertura (como estaba) o inventar un
módulo Caja completo (fuera de alcance de Aportes), se redefine FR-006 como
un contrato de datos: Aportes garantiza que el dato existe y es sumable;
otro módulo hace la suma real. Esto es consistente con
`docs/arquitectura-general.md` ("un módulo puede referenciar... pero no debe
reimplementar la lógica de otro módulo").

**Alternatives considered**: implementar ya una entidad `Caja` con saldo
acumulado — descartado por invadir el alcance del módulo 6 de la
constitución antes de que exista su propio spec/plan.

## 10. Determinación del mes de ingreso (resuelve G2 HIGH)

**Decision**: El mes de ingreso de un socio a un periodo se deriva de
`AporteInicial.fecha` (US1) — no se añade un campo nuevo a `Socio` ni a
`AporteMensual`. `resolverMesesHasta` nunca genera un mes anterior a
`YearMonth.from(AporteInicial.fecha)`.

**Rationale**: `AporteInicial` ya se registra exactamente una vez por
socio+periodo al momento de su ingreso (US1); reutilizar su `fecha` evita
duplicar el dato en otra entidad y mantiene la fuente de verdad en un solo
lugar (no hay forma de que un socio tenga `AporteMensual` sin antes tener
`AporteInicial`, por la relación de dependencia entre historias US1→US2).

**Alternatives considered**: añadir `fechaIngreso` a la entidad `Socio` —
descartado porque `Socio` es una entidad externa (propiedad del módulo
Registro de socios) y modificarla violaría el límite de responsabilidad
entre módulos de `docs/arquitectura-general.md`.

## 11. Seguridad por rol en `AporteController` (resuelve C1 CRITICAL)

**Decision**: Cada método de `AporteController` se anota con
`@PreAuthorize("hasRole('ADMINISTRADOR')")`. Un test de integración
`AporteControllerIT` verifica que un usuario con rol `SOCIO` recibe
403/redirect en las 3 rutas, y que `ADMINISTRADOR` accede correctamente.

**Rationale**: El hallazgo C1 detectó que, pese a que Spring Security era
dependencia declarada (constitución §3) y `contracts/aportes-endpoints.md`
ya documentaba "Rol requerido: ADMINISTRADOR" en cada ruta, ninguna tarea
implementaba ni probaba esa restricción — Principio IX ("Seguridad no es
opcional") no puede quedar como una nota de documentación sin verificación
ejecutable.

**Alternatives considered**: validar el rol manualmente dentro de
`AporteService` — descartado porque el Service no debe conocer detalles de
autenticación/HTTP (separación de capas); `@PreAuthorize` a nivel de
Controller es el mecanismo estándar de Spring Security y el que asume
`docs/arquitectura-general.md` §Seguridad.

## Resumen de resolución de NEEDS CLARIFICATION

Ninguno en el Technical Context original. Las secciones 1–6 son decisiones
de diseño de dominio iniciales; las secciones 7–11 documentan la
remediación de los hallazgos CRITICAL/HIGH del primer `/speckit.analyze`
(U1, U2, G1, G2, C1).
