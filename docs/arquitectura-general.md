# Arquitectura General — Mi Banquito

Este documento define la convención de arquitectura que **todos** los módulos
del sistema (Autenticación, Periodos, Socios, Aportes, Préstamos, Caja, Estado
de cuenta, Reportes, Cierre anual, Renovación) deben seguir. Complementa a
`.specify/memory/constitution.md`, que define los invariantes de negocio y las
restricciones técnicas obligatorias; este documento traduce esas restricciones
en una convención concreta de paquetes y capas.

## Estilo arquitectónico

Monolito Spring Boot MVC organizado **por capas técnicas**, no por módulo de
negocio (Principio IV de la constitución: simplicidad sobre sofisticación).

```
Controller → Service → Repository → Entity
```

- **Controller**: recibe requests HTTP (Thymeleaf/form-backed), valida entrada
  (Jakarta Bean Validation), delega en el Service, nunca contiene lógica de
  negocio.
- **Service**: contiene toda la lógica de negocio y las reglas financieras
  (Principio II). Es la única capa que debe testearse con TDD para lógica
  crítica (Principio III).
- **Repository**: interfaces Spring Data JPA, sin lógica de negocio.
- **Entity**: clases JPA que mapean tablas MySQL. Todo campo monetario usa
  `BigDecimal` (Principio VIII).
- **DTO**: objetos de transferencia entre Controller y Service/vistas cuando
  la Entity no debe exponerse directamente (p. ej. formularios con campos que
  no son 1:1 con la entidad, o vistas que agregan datos de varias entidades).

## Convención de paquetes por módulo

Cada módulo de negocio (ver constitución §5) vive en su propio subpaquete bajo
`com.mibanquito`, y dentro de él se repiten las mismas subcarpetas por capa:

```
com.mibanquito.<modulo>/
├── entity/
├── repository/
├── service/
├── dto/
└── controller/
```

Ejemplo para el módulo Aportes: `com.mibanquito.aportes.entity`,
`com.mibanquito.aportes.repository`, `com.mibanquito.aportes.service`,
`com.mibanquito.aportes.dto`, `com.mibanquito.aportes.controller`.

**Regla de dependencia entre módulos**: un módulo puede referenciar entidades
de otro módulo por su tipo (p. ej. `Socio`, `PeriodoAnual`) para declarar
relaciones JPA (`@ManyToOne`), pero no debe reimplementar ni duplicar la lógica
de negocio de otro módulo. Ejemplo: Aportes referencia `Socio` y `PeriodoAnual`
(entidades de otros módulos) pero no gestiona su ciclo de vida.

## Esquema de base de datos y migraciones

- MySQL 8. Flyway es la única autoridad del esquema (constitución §3):
  ningún cambio de esquema se aplica fuera de una migración versionada.
- Convención de nombres: `V<n>__<descripcion_snake_case>.sql`, numeración
  global y creciente para todo el proyecto (no reinicia por módulo).
- Toda tabla que registre un movimiento de negocio (aportes, préstamos,
  intereses, multas) MUST tener FK a `periodo_anual` (Principio X).

## Testing

- JUnit 5 + Mockito para pruebas unitarias de la capa Service — obligatorias
  para toda lógica financiera (Principio III): test-first, la prueba debe
  existir y fallar antes de escribir la implementación.
- Testcontainers es opcional para pruebas de integración de Repository/
  Controller (requiere Docker Desktop); no es obligatorio para que un módulo
  se considere completo.

## Seguridad

Spring Security con control de acceso por rol (`ADMINISTRADOR` / `SOCIO`,
constitución §4) aplicado a nivel de Controller. Ningún Service asume que ya
se validó el rol; la anotación de seguridad vive en el endpoint.

## Build y control de versiones

Maven (un único `pom.xml` en la raíz del repositorio, sin módulos Maven
multi-proyecto). Git + GitHub, con un commit al cierre de cada fase SDD
(spec → plan → tasks → implement).
