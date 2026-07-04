# Architecture

Both deployable modules (`api`, `app`) follow a hexagonal (ports & adapters) structure. Each module is one Spring Boot application with its own `@SpringBootApplication` class; the package layout below repeats identically inside each module's own root package (`org.example.api`, `org.example.app`).

## Package tree

```
domain/                         pure Kotlin. No Spring/JPA imports, no annotations.
application/
  port/`in`/                    interfaces = use cases, called by driving adapters.
  port/out/                     interfaces = what application services need from the outside world.
  service/                      @Service classes implementing port.`in`, depending only on port.out + domain.
adapter/
  `in`/web|scheduler|event/     driving adapters (controllers, @Scheduled triggers, @EventListener).
                                 Depend on a port.`in` interface, never on the concrete service class.
  out/client|persistence/       driven adapters implementing port.out.
                                 JPA @Entity, Spring Data repos, resilience4j-guarded clients live only here.
config/                         pure Spring wiring that isn't part of the hexagon
                                 (SecurityConfig, GlobalExceptionHandler). Not a port or adapter.
```

## Dependency rule

`adapter → application → domain`, never the reverse. Adapters never depend on each other (e.g. `adapter.in.web` never calls into `adapter.out.persistence` directly — it only ever talks through an `application.port.in` interface).

`domain` classes have zero framework imports — no `@Entity`, no Spring annotations. Persistence is modeled twice on purpose: a plain `domain.PendingDepartmentRequest` (or equivalent) that `application` and `domain` code work with, and a separate `@Entity`-annotated persistence-only class inside `adapter.out.persistence`, mapped between the two by the persistence adapter. This is more ceremony than letting a JPA entity double as the domain model, but it means the domain layer never has to change because of a schema/ORM concern.

## Kotlin gotcha: `in` is a hard keyword

`in` cannot appear unescaped as a package-name segment in Kotlin — every `package`/`import` line under `port.in`/`adapter.in` needs a backtick:

```kotlin
package org.example.api.application.port.`in`
import org.example.api.application.port.`in`.DepartmentLookupUseCase
```

`out` is only a variance modifier keyword, not a hard keyword, so it needs no escaping. The on-disk folder is still just named `in` — only the Kotlin source tokens need the backtick.

## Spring Modulith note

Both modules pull in `spring-modulith-starter-test` and `app` has one `@ApplicationModuleTest`. Spring Modulith derives "application modules" from the packages directly under each module's `@SpringBootApplication` package — after this refactor that means `domain`, `application`, `adapter`, and `config` are the four modules Modulith sees, which is **layer-oriented, not feature-oriented**. That's harmless today (one feature per module, and there's no `ApplicationModules.of(...).verify()` structural test enforcing module boundaries anywhere in the repo), but if a second feature is ever added to either module, both features will land inside the same `adapter`/`application`/`domain` packages and Modulith will treat them as a single module — silently losing any module-boundary value Modulith could otherwise provide. Worth revisiting (e.g. feature-first subpackages under each layer) if/when that happens.

## Example: Department lookup (`api` module)

```
domain/PendingDepartmentRequest.kt, DepartmentLookupResult.kt
application/port/in/DepartmentLookupUseCase.kt, ReprocessPendingDepartmentsUseCase.kt
application/port/out/DepartmentGateway.kt, PendingDepartmentRequestPort.kt
application/service/DepartmentLookupService.kt, DepartmentOutboxProcessor.kt
adapter/in/web/DepartmentController.kt
adapter/in/scheduler/DepartmentOutboxScheduler.kt
adapter/out/client/ExternalDepartmentClient.kt              (resilience4j-guarded, implements DepartmentGateway)
adapter/out/persistence/PendingDepartmentRequestJpaEntity.kt, PendingDepartmentRequestJpaRepository.kt, PendingDepartmentRequestPersistenceAdapter.kt
```

See `docs/resilience4j.md` for the resilience4j configuration details for this feature.
