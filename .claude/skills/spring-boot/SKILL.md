# Skill: Spring Boot 3.5 — project patterns

## Pinned versions
- Spring Boot **3.5.x** (do not migrate to 4.x without architectural approval).
- Kotlin **2.2.0** with virtual threads enabled.
- `spring.threads.virtual.enabled=true` in `application.yml`.

## Stereotypes - which annotation to use (hexagonal layout)
- `@RestController` → classes in `adapter/in/web/`. Implements/calls a `port.in` use case interface, no business rules. Only HTTP mapping.
- `@Service` → classes in `application/service/`. Implements a `port.in` use case interface, depends only on `port.out` interfaces + `domain` types. Business rules and `@Transactional` live here.
- `@Repository`/JPA → interfaces extending `JpaRepository`, plus the `@Entity` class and a persistence adapter implementing a `port.out` interface — all confined to `adapter/out/persistence/`. Never let the JPA entity or the Spring Data repository leak outside that package; `application` only ever sees the `port.out` interface.
- `@Configuration` → classes in `config/`. Exposed beans, cross-cutting Spring wiring, not part of the hexagon.
- `@Component` → generic. Used for other driving/driven adapters that don't fit the above (`adapter/in/scheduler`, `adapter/in/event`, `adapter/out/client`). **Avoid** elsewhere if one of the above fits.

## Dependency injection
- ✅ CORRECT - constructor injection (final fields)
- ❌ WRONG - @Autowired on field

[continues with exception handling, validation, properties, actuator…]